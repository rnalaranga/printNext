package com.rr.secureprint.service;

import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service for Active Directory / LDAP authentication and identity resolution.
 * Enforces Windows User SID as the primary immutable identifier of job ownership.
 */
@Service
public class ActiveDirectoryService {

    private static final Logger LOGGER = Logger.getLogger(ActiveDirectoryService.class.getName());

    @Value("${app.ad.enabled:false}")
    private boolean adEnabled;

    @Value("${app.ad.ldap-url:ldaps://dc01.rrconstruction.local:636}")
    private String ldapUrl;

    @Value("${app.ad.domain:rrconstruction.local}")
    private String domain;

    @Value("${app.ad.base-dn:DC=rrconstruction,DC=local}")
    private String baseDn;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrintJobRepository printJobRepository;

    public ActiveDirectoryService(UserRepository userRepository, PasswordEncoder passwordEncoder, PrintJobRepository printJobRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.printJobRepository = printJobRepository;
    }

    /**
     * Authenticates credentials against Active Directory via LDAPS (if enabled)
     * or against local verified identities (in dev/simulation mode).
     */
    public Optional<UserEntity> authenticate(String username, String password) {
        if (adEnabled) {
            return authenticateLdap(username, password);
        } else {
            // Local fallback simulation mode
            return userRepository.findByUsername(username.toLowerCase().trim())
                    .filter(UserEntity::isActive)
                    .filter(u -> u.getPinHash() != null && (password.equals(u.getPinHash()) || passwordEncoder.matches(password, u.getPinHash())));
        }
    }

    /**
     * Authenticates an employee at the Toshiba MFP touchscreen using their quick PIN code.
     */
    public Optional<UserEntity> authenticateByPin(String pin) {
        if (pin == null || pin.isBlank()) {
            return Optional.empty();
        }
        String cleanPin = pin.trim();
        List<UserEntity> matchingUsers = userRepository.findAll().stream()
                .filter(UserEntity::isActive)
                .filter(u -> u.getPinHash() != null && (cleanPin.equals(u.getPinHash()) || passwordEncoder.matches(cleanPin, u.getPinHash())))
                .toList();

        if (matchingUsers.isEmpty()) {
            return Optional.empty();
        }
        if (matchingUsers.size() == 1) {
            return Optional.of(matchingUsers.getFirst());
        }

        try {
            List<PrintJob> heldJobs = printJobRepository.findByStatus(JobStatus.HELD);
            for (UserEntity candidate : matchingUsers) {
                boolean hasHeldJobs = heldJobs.stream().anyMatch(j ->
                        (j.getUserSid() != null && j.getUserSid().equalsIgnoreCase(candidate.getUserSid())) ||
                        (j.getUsername() != null && j.getUsername().equalsIgnoreCase(candidate.getUsername())) ||
                        (j.getUser() != null && j.getUser().getId().equals(candidate.getId()))
                );
                if (hasHeldJobs) {
                    LOGGER.info("[PIN AUTH] Multiple matching users for PIN " + cleanPin + ". Prioritized candidate '" + candidate.getUsername() + "' with held jobs.");
                    return Optional.of(candidate);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[PIN AUTH] Could not query held jobs during PIN disambiguation: " + e.getMessage());
        }

        return Optional.of(matchingUsers.getFirst());
    }

    /**
     * Authenticates an employee by username and PIN code.
     */
    public Optional<UserEntity> authenticateByUsernameAndPin(String username, String pin) {
        if (username == null || username.isBlank() || pin == null || pin.isBlank()) {
            return Optional.empty();
        }
        String cleanPin = pin.trim();
        return userRepository.findByUsername(username.toLowerCase().trim())
                .filter(UserEntity::isActive)
                .filter(u -> u.getPinHash() != null && (cleanPin.equals(u.getPinHash()) || passwordEncoder.matches(cleanPin, u.getPinHash())));
    }

    /**
     * Authenticates an employee via RFID card / badge scanner swipe.
     */
    public Optional<UserEntity> authenticateByBadge(String badgeId) {
        if (badgeId == null || badgeId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByBadgeId(badgeId.trim())
                .filter(UserEntity::isActive);
    }

    /**
     * Authenticates against Active Directory Domain Controller using LDAPS.
     */
    private Optional<UserEntity> authenticateLdap(String username, String password) {
        String userPrincipal = username.contains("@") ? username : username + "@" + domain;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, userPrincipal);
        env.put(Context.SECURITY_CREDENTIALS, password);
        // Request binary objectSid
        env.put("java.naming.ldap.attributes.binary", "objectSid");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            LOGGER.info("Active Directory LDAPS bind successful for: " + userPrincipal);

            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{"sAMAccountName", "displayName", "mail", "department", "objectSid"});

            String searchFilter = "(&(objectClass=user)(sAMAccountName=" + sanitizeLdapInput(username) + "))";
            NamingEnumeration<SearchResult> answer = ctx.search(baseDn, searchFilter, controls);

            if (answer.hasMore()) {
                SearchResult result = answer.next();
                Attributes attrs = result.getAttributes();

                String sAMAccountName = getAttr(attrs, "sAMAccountName");
                String displayName = getAttr(attrs, "displayName");
                String mail = getAttr(attrs, "mail");
                String dept = getAttr(attrs, "department");
                byte[] sidBytes = (byte[]) attrs.get("objectSid").get();
                String stringSid = convertBinarySidToString(sidBytes);

                // Synchronize or retrieve local user entity
                return Optional.of(userRepository.findByUserSid(stringSid).orElseGet(() -> {
                    UserEntity newUser = new UserEntity(sAMAccountName, displayName, stringSid, null, null, mail, dept, "USER");
                    return userRepository.save(newUser);
                }));
            }
        } catch (Exception e) {
            LOGGER.warning("Active Directory authentication failed for " + username + ": " + e.getMessage());
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (Exception ignored) {}
            }
        }
        return Optional.empty();
    }

    private String getAttr(Attributes attrs, String attrName) {
        try {
            Attribute a = attrs.get(attrName);
            return a != null ? a.get().toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String sanitizeLdapInput(String input) {
        return input.replace("\\", "\\5c")
                    .replace("*", "\\2a")
                    .replace("(", "\\28")
                    .replace(")", "\\29")
                    .replace("\0", "\\00");
    }

    /**
     * Converts Active Directory binary objectSid to standard Windows string SID (e.g. S-1-5-21-...).
     */
    public static String convertBinarySidToString(byte[] sid) {
        if (sid == null || sid.length < 8) {
            return "S-1-0-0";
        }
        StringBuilder sb = new StringBuilder("S-");
        // Revision
        sb.append(sid[0] & 0xFF).append("-");

        // Authority (6 bytes, big endian)
        long authority = 0;
        for (int i = 2; i <= 7; i++) {
            authority = (authority << 8) + (sid[i] & 0xFF);
        }
        sb.append(authority);

        // Subauthorities count
        int count = sid[1] & 0xFF;
        for (int i = 0; i < count; i++) {
            int offset = 8 + (i * 4);
            long subAuth = (sid[offset] & 0xFFL)
                    | ((sid[offset + 1] & 0xFFL) << 8)
                    | ((sid[offset + 2] & 0xFFL) << 16)
                    | ((sid[offset + 3] & 0xFFL) << 24);
            sb.append("-").append(subAuth);
        }
        return sb.toString();
    }
}
