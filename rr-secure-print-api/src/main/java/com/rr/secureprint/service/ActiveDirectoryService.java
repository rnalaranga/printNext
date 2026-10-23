package com.rr.secureprint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rr.secureprint.dto.DirectoryConfigDto;
import com.rr.secureprint.dto.DirectorySyncResultDto;
import com.rr.secureprint.entity.DirectoryConfigEntity;
import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.DirectoryConfigRepository;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for Active Directory / LDAP synchronization, Windows user import,
 * and PaperCut-style Find-Me print job ownership auto-provisioning.
 */
@Service
public class ActiveDirectoryService {

    private static final Logger LOGGER = Logger.getLogger(ActiveDirectoryService.class.getName());

    @Value("${app.ad.enabled:false}")
    private boolean adEnabled;

    @Value("${app.ad.ldap-url:ldap://127.0.0.1:389}")
    private String defaultLdapUrl;

    @Value("${app.ad.domain:qoa.local}")
    private String defaultDomain;

    @Value("${app.ad.base-dn:DC=qoa,DC=local}")
    private String defaultBaseDn;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrintJobRepository printJobRepository;
    private final DirectoryConfigRepository directoryConfigRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ActiveDirectoryService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  PrintJobRepository printJobRepository,
                                  DirectoryConfigRepository directoryConfigRepository,
                                  AuditService auditService,
                                  ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.printJobRepository = printJobRepository;
        this.directoryConfigRepository = directoryConfigRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /* =========================================================================
       1. DIRECTORY CONFIGURATION MANAGEMENT
       ========================================================================= */

    public DirectoryConfigEntity getOrCreateConfig() {
        return directoryConfigRepository.findById(1L).orElseGet(() -> {
            DirectoryConfigEntity config = new DirectoryConfigEntity();
            config.setId(1L);
            config.setDirectoryType("ACTIVE_DIRECTORY");
            config.setLdapUrl(defaultLdapUrl);
            config.setDomainName(defaultDomain);
            config.setBaseDn(defaultBaseDn);
            config.setBindUser("Administrator@" + defaultDomain);
            config.setUserSearchFilter("(&(objectCategory=person)(objectClass=user))");
            config.setDefaultPin("1234");
            config.setEnabled(true);
            config.setLastSyncStatus("READY");
            config.setLastSyncMessage("Active Directory (" + defaultDomain + ") configured.");
            return directoryConfigRepository.save(config);
        });
    }

    public DirectoryConfigDto getDirectoryConfigDto() {
        return DirectoryConfigDto.fromEntity(getOrCreateConfig(), true);
    }

    @Transactional
    public DirectoryConfigDto saveDirectoryConfig(DirectoryConfigDto dto, String adminActor, String clientIp) {
        DirectoryConfigEntity config = getOrCreateConfig();
        if (dto.ldapUrl() != null && !dto.ldapUrl().isBlank()) config.setLdapUrl(dto.ldapUrl().trim());
        if (dto.domainName() != null && !dto.domainName().isBlank()) config.setDomainName(dto.domainName().trim());
        if (dto.baseDn() != null && !dto.baseDn().isBlank()) config.setBaseDn(dto.baseDn().trim());
        if (dto.bindUser() != null) config.setBindUser(dto.bindUser().trim());
        if (dto.bindPassword() != null && !dto.bindPassword().isBlank() && !dto.bindPassword().equals("********")) {
            config.setBindPassword(dto.bindPassword().trim());
        }
        if (dto.userSearchFilter() != null && !dto.userSearchFilter().isBlank()) {
            config.setUserSearchFilter(dto.userSearchFilter().trim());
        }
        if (dto.defaultPin() != null && !dto.defaultPin().isBlank()) {
            config.setDefaultPin(dto.defaultPin().trim());
        }
        config.setEnabled(dto.isEnabled());
        config.setUpdatedAt(Instant.now());

        DirectoryConfigEntity saved = directoryConfigRepository.save(config);
        auditService.logEvent("DIRECTORY_CONFIG_UPDATED", null, adminActor, null, null, clientIp,
                "Updated directory configuration for domain: " + saved.getDomainName(), "SUCCESS");

        return DirectoryConfigDto.fromEntity(saved, true);
    }

    /* =========================================================================
       2. LDAP CONNECTION TESTING
       ========================================================================= */

    public Map<String, Object> testConnection(DirectoryConfigDto dto) {
        String ldapUrl = (dto != null && dto.ldapUrl() != null && !dto.ldapUrl().isBlank())
                ? dto.ldapUrl().trim() : getOrCreateConfig().getLdapUrl();
        String domain = (dto != null && dto.domainName() != null && !dto.domainName().isBlank())
                ? dto.domainName().trim() : getOrCreateConfig().getDomainName();
        String baseDn = (dto != null && dto.baseDn() != null && !dto.baseDn().isBlank())
                ? dto.baseDn().trim() : getOrCreateConfig().getBaseDn();

        // 1. Probe TCP socket connection
        try {
            String host = "127.0.0.1";
            int port = 389;
            try {
                URI uri = URI.create(ldapUrl);
                if (uri.getHost() != null) host = uri.getHost();
                if (uri.getPort() > 0) port = uri.getPort();
            } catch (Exception e) {
                // Keep default
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000);
            }
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "message", "TCP connection to LDAP server failed: " + e.getMessage(),
                    "userCount", 0
            );
        }

        // 2. Query Active Directory / LDAP user count
        try {
            List<Map<String, String>> users = queryActiveDirectoryViaBridge(domain, baseDn, 100);
            return Map.of(
                    "success", true,
                    "message", "Successfully connected to Active Directory (" + domain + "). Found " + users.size() + " active user accounts.",
                    "userCount", users.size(),
                    "sampleUsers", users.stream().map(u -> u.get("samAccountName")).filter(Objects::nonNull).limit(5).toList()
            );
        } catch (Exception e) {
            return Map.of(
                    "success", true,
                    "message", "LDAP port reachable. Directory probe notice: " + e.getMessage(),
                    "userCount", 0
            );
        }
    }

    /* =========================================================================
       3. ACTIVE DIRECTORY / LDAP USER SYNCHRONIZATION
       ========================================================================= */

    @Transactional
    public DirectorySyncResultDto syncActiveDirectoryUsers(String adminActor, String clientIp) {
        DirectoryConfigEntity config = getOrCreateConfig();
        String domain = config.getDomainName();
        String baseDn = config.getBaseDn();
        String defaultPin = config.getDefaultPin();

        LOGGER.info("[AD SYNC] Initiating synchronization with Active Directory (" + domain + ")...");

        List<Map<String, String>> adUsers;
        try {
            adUsers = queryActiveDirectoryViaBridge(domain, baseDn, 1000);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[AD SYNC ERROR] Failed to query Active Directory: " + e.getMessage(), e);
            config.setLastSyncAt(Instant.now());
            config.setLastSyncStatus("FAILED");
            config.setLastSyncMessage("Failed to query Active Directory: " + e.getMessage());
            directoryConfigRepository.save(config);
            return new DirectorySyncResultDto(false, "Active Directory sync failed: " + e.getMessage(), 0, 0, 0, List.of());
        }

        int importedCount = 0;
        int updatedCount = 0;
        List<String> userSummary = new ArrayList<>();

        for (Map<String, String> u : adUsers) {
            String sam = u.get("samAccountName");
            if (sam == null || sam.isBlank() || sam.endsWith("$")) continue;

            String cleanUsername = sam.trim().toLowerCase();
            // Skip disabled default accounts
            if (cleanUsername.equals("krbtgt") || cleanUsername.equals("guest")) continue;

            String displayName = u.get("displayName");
            if (displayName == null || displayName.isBlank()) displayName = sam;

            String email = u.get("mail");
            if (email == null || email.isBlank()) email = cleanUsername + "@" + domain;

            String department = u.get("department");
            if (department == null || department.isBlank()) department = "Active Directory";

            String sid = u.get("sid");
            if (sid == null || sid.isBlank()) {
                sid = "S-1-5-21-" + Math.abs(cleanUsername.hashCode()) + "-100" + (userRepository.count() + 1);
            }

            Optional<UserEntity> existingOpt = userRepository.findByUserSid(sid)
                    .or(() -> userRepository.findByUsername(cleanUsername));

            if (existingOpt.isPresent()) {
                UserEntity existing = existingOpt.get();
                existing.setDisplayName(displayName);
                existing.setEmail(email);
                existing.setDepartment(department);
                existing.setAuthSource("ACTIVE_DIRECTORY");
                if (existing.getUserSid() == null || !existing.getUserSid().startsWith("S-1-5-")) {
                    existing.setUserSid(sid);
                }
                userRepository.save(existing);
                updatedCount++;
                userSummary.add(cleanUsername + " (Updated from AD)");
            } else {
                UserEntity newUser = new UserEntity(
                        cleanUsername,
                        displayName,
                        sid,
                        defaultPin,
                        "BADGE-" + Math.abs(cleanUsername.hashCode() % 10000),
                        email,
                        department,
                        cleanUsername.equals("administrator") ? "ADMIN" : "USER",
                        "ACTIVE_DIRECTORY"
                );
                userRepository.save(newUser);
                importedCount++;
                userSummary.add(cleanUsername + " (Imported from AD)");
            }
        }

        config.setLastSyncAt(Instant.now());
        config.setLastSyncStatus("SUCCESS");
        config.setSyncedUserCount(importedCount + updatedCount);
        config.setLastSyncMessage("Successfully synchronized " + (importedCount + updatedCount) + " users from Active Directory (" + domain + ").");
        directoryConfigRepository.save(config);

        auditService.logEvent("AD_SYNC_SUCCESS", null, adminActor, null, null, clientIp,
                "Synchronized " + importedCount + " new users and updated " + updatedCount + " users from AD " + domain, "SUCCESS");

        LOGGER.info("[AD SYNC] Finished: " + importedCount + " imported, " + updatedCount + " updated.");
        return new DirectorySyncResultDto(
                true,
                "Successfully synchronized Active Directory (" + domain + "). " + importedCount + " imported, " + updatedCount + " updated.",
                adUsers.size(),
                importedCount,
                updatedCount,
                userSummary
        );
    }

    /* =========================================================================
       4. LOCAL WINDOWS USERS SCAN & IMPORT
       ========================================================================= */

    @Transactional
    public DirectorySyncResultDto importWindowsUsers(String adminActor, String clientIp) {
        LOGGER.info("[WINDOWS USER IMPORT] Scanning local Windows user accounts...");
        List<Map<String, String>> winUsers;
        try {
            winUsers = queryLocalWindowsUsers();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[WINDOWS USER IMPORT ERROR] " + e.getMessage(), e);
            return new DirectorySyncResultDto(false, "Failed to scan local Windows users: " + e.getMessage(), 0, 0, 0, List.of());
        }

        int importedCount = 0;
        int updatedCount = 0;
        List<String> userSummary = new ArrayList<>();

        for (Map<String, String> u : winUsers) {
            String name = u.get("name");
            if (name == null || name.isBlank() || name.endsWith("$")) continue;

            String clean = name.trim().toLowerCase();
            if (clean.equals("guest") || clean.equals("defaultaccount") || clean.equals("wdagutilityaccount")) continue;

            String displayName = u.get("fullName");
            if (displayName == null || displayName.isBlank()) displayName = name;

            String sid = u.get("sid");
            if (sid == null || sid.isBlank()) {
                sid = "S-1-5-21-" + Math.abs(clean.hashCode()) + "-50" + (userRepository.count() + 1);
            }

            Optional<UserEntity> existingOpt = userRepository.findByUserSid(sid)
                    .or(() -> userRepository.findByUsername(clean));

            if (existingOpt.isPresent()) {
                UserEntity existing = existingOpt.get();
                existing.setDisplayName(displayName);
                existing.setAuthSource("WINDOWS_LOCAL");
                if (existing.getUserSid() == null || !existing.getUserSid().startsWith("S-1-5-")) {
                    existing.setUserSid(sid);
                }
                userRepository.save(existing);
                updatedCount++;
                userSummary.add(clean + " (Updated Windows User)");
            } else {
                UserEntity newUser = new UserEntity(
                        clean,
                        displayName,
                        sid,
                        "1234",
                        "BADGE-" + Math.abs(clean.hashCode() % 10000),
                        clean + "@windows.local",
                        "Windows Local",
                        clean.equals("administrator") ? "ADMIN" : "USER",
                        "WINDOWS_LOCAL"
                );
                userRepository.save(newUser);
                importedCount++;
                userSummary.add(clean + " (Imported Windows User)");
            }
        }

        auditService.logEvent("WINDOWS_USER_IMPORT_SUCCESS", null, adminActor, null, null, clientIp,
                "Imported " + importedCount + " and updated " + updatedCount + " local Windows users.", "SUCCESS");

        return new DirectorySyncResultDto(
                true,
                "Successfully imported " + importedCount + " new and updated " + updatedCount + " Windows users.",
                winUsers.size(),
                importedCount,
                updatedCount,
                userSummary
        );
    }

    /* =========================================================================
       5. PAPERCUT-STYLE JIT DYNAMIC USER RESOLUTION & PROVISIONING ON PRINT
       ========================================================================= */

    /**
     * Resolves an authentic user identity when a print job arrives at the virtual queue.
     * If the user doesn't exist yet, auto-provisions them with their Windows / AD identity
     * and default PIN (1234), placing the print job under their account.
     */
    @Transactional
    public UserEntity resolveOrProvisionUser(String rawUsername, String userSid) {
        if (rawUsername == null || rawUsername.isBlank()) {
            rawUsername = "administrator";
        }
        String cleanUsername = rawUsername.trim();
        if (cleanUsername.contains("\\")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('\\') + 1);
        } else if (cleanUsername.contains("@")) {
            cleanUsername = cleanUsername.substring(0, cleanUsername.indexOf('@'));
        }
        cleanUsername = cleanUsername.trim().toLowerCase();

        final String finalCleanUsername = cleanUsername;
        final String finalRawUsername = rawUsername.trim().toLowerCase();
        final String effectiveSid = (userSid != null && !userSid.isBlank() && userSid.startsWith("S-1-5-"))
                ? userSid.trim()
                : null;

        // 1. Check database by exact Windows SID
        if (effectiveSid != null) {
            Optional<UserEntity> bySid = userRepository.findByUserSid(effectiveSid);
            if (bySid.isPresent()) return bySid.get();
        }

        // 2. Check database by normalized clean username
        Optional<UserEntity> byCleanUsername = userRepository.findByUsername(finalCleanUsername);
        if (byCleanUsername.isPresent()) return byCleanUsername.get();

        // 3. Check database by raw username (e.g. domain/user)
        Optional<UserEntity> byRawUsername = userRepository.findByUsername(finalRawUsername);
        if (byRawUsername.isPresent()) return byRawUsername.get();

        // 4. Auto-provision user (PaperCut JIT Mode)
        String assignedSid = effectiveSid != null ? effectiveSid : "S-1-5-21-" + Math.abs(finalCleanUsername.hashCode()) + "-100" + (userRepository.count() + 1);
        String displayName = Character.toUpperCase(finalCleanUsername.charAt(0)) + finalCleanUsername.substring(1) + " (Windows User)";

        DirectoryConfigEntity config = getOrCreateConfig();
        String defaultPin = config.getDefaultPin() != null ? config.getDefaultPin() : "1234";

        UserEntity newUser = new UserEntity(
                finalCleanUsername,
                displayName,
                assignedSid,
                defaultPin,
                "BADGE-" + Math.abs(finalCleanUsername.hashCode() % 10000),
                finalCleanUsername + "@" + config.getDomainName(),
                "Windows Print Pool",
                finalCleanUsername.equals("administrator") ? "ADMIN" : "USER",
                "WINDOWS_LOCAL"
        );

        UserEntity saved = userRepository.save(newUser);
        LOGGER.info("[PAPERCUT JIT PROVISIONING] Auto-created user '" + saved.getUsername() + "' (SID: " + saved.getUserSid() + ", PIN: " + defaultPin + ") for incoming print job.");
        return saved;
    }

    /* =========================================================================
       6. EMPLOYEE AUTHENTICATION (PIN, CREDENTIALS, BADGE)
       ========================================================================= */

    public Optional<UserEntity> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }

        String cleanUsername = username.trim();
        if (cleanUsername.contains("\\")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('\\') + 1);
        } else if (cleanUsername.contains("/")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('/') + 1);
        } else if (cleanUsername.contains("@")) {
            cleanUsername = cleanUsername.substring(0, cleanUsername.indexOf('@'));
        }

        DirectoryConfigEntity config = getOrCreateConfig();
        boolean isAdActive = adEnabled || (config != null && config.isEnabled());

        // 1. Try Active Directory / LDAP authentication first
        if (isAdActive) {
            Optional<UserEntity> adUser = authenticateLdap(cleanUsername, password);
            if (adUser.isPresent()) {
                LOGGER.info("[AUTH] User '" + cleanUsername + "' authenticated via Active Directory LDAP.");
                return adUser;
            }
        }

        // 2. Check local database for user (Manual, Local, or existing synced user)
        Optional<UserEntity> localUserOpt = userRepository.findByUsername(cleanUsername.toLowerCase().trim());
        if (localUserOpt.isPresent() && localUserOpt.get().isActive()) {
            UserEntity user = localUserOpt.get();

            // 2a. Check custom password_hash
            if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
                if (password.equals(user.getPasswordHash()) || passwordEncoder.matches(password, user.getPasswordHash())) {
                    LOGGER.info("[AUTH] User '" + cleanUsername + "' authenticated via database password.");
                    return Optional.of(user);
                }
            }

            // 2b. Check PIN as password fallback (e.g. if user types their PIN into password box)
            if (user.getPinHash() != null && !user.getPinHash().isBlank()) {
                if (password.equals(user.getPinHash()) || passwordEncoder.matches(password, user.getPinHash())) {
                    LOGGER.info("[AUTH] User '" + cleanUsername + "' authenticated via database PIN.");
                    return Optional.of(user);
                }
            }

            // 2c. Verify via Windows security (Domain or Machine Account)
            if (validateWindowsCredentials(cleanUsername, password)) {
                LOGGER.info("[AUTH] User '" + cleanUsername + "' authenticated via Windows Security PrincipalContext.");
                return Optional.of(user);
            }
        }

        // 3. Fallback: try Windows Domain/Local validation for unenrolled accounts (JIT auto-provisioning)
        if (validateWindowsCredentials(cleanUsername, password)) {
            LOGGER.info("[AUTH] Windows domain/local account '" + cleanUsername + "' verified. Enrolling user...");
            return Optional.of(resolveOrProvisionUser(cleanUsername, null));
        }

        return Optional.empty();
    }

    private boolean validateWindowsCredentials(String username, String password) {
        try {
            String cleanUser = username.replace("'", "''");
            String cleanPass = password.replace("'", "''");
            String script = "Add-Type -AssemblyName System.DirectoryServices.AccountManagement; " +
                    "$valid = $false; " +
                    "$comp = Get-CimInstance Win32_ComputerSystem; " +
                    "if ($comp.PartOfDomain) { " +
                    "  try { " +
                    "    $pcDomain = New-Object System.DirectoryServices.AccountManagement.PrincipalContext([System.DirectoryServices.AccountManagement.ContextType]::Domain, $comp.Domain); " +
                    "    $valid = $pcDomain.ValidateCredentials('" + cleanUser + "', '" + cleanPass + "'); " +
                    "  } catch {} " +
                    "} " +
                    "if (-not $valid) { " +
                    "  try { " +
                    "    $pcMachine = New-Object System.DirectoryServices.AccountManagement.PrincipalContext([System.DirectoryServices.AccountManagement.ContextType]::Machine); " +
                    "    $valid = $pcMachine.ValidateCredentials('" + cleanUser + "', '" + cleanPass + "'); " +
                    "  } catch {} " +
                    "} " +
                    "Write-Output $valid";
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.trim().equalsIgnoreCase("True")) {
                        return true;
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            LOGGER.fine("Windows credential validation exception: " + e.getMessage());
        }
        return false;
    }

    public Optional<UserEntity> authenticateByPin(String pin) {
        if (pin == null || pin.isBlank()) {
            return Optional.empty();
        }
        String cleanPin = pin.trim();

        // 1. Fast indexed SQL query for 13,000+ users
        List<UserEntity> directMatches = userRepository.findByPinHash(cleanPin).stream()
                .filter(UserEntity::isActive)
                .toList();
        List<UserEntity> matchingUsers = directMatches;

        // 2. Fallback to BCrypt hashed check if direct match yielded nothing
        if (matchingUsers.isEmpty()) {
            matchingUsers = userRepository.findAll().stream()
                    .filter(UserEntity::isActive)
                    .filter(u -> u.getPinHash() != null && passwordEncoder.matches(cleanPin, u.getPinHash()))
                    .toList();
        }

        if (matchingUsers.isEmpty()) {
            return Optional.empty();
        }
        if (matchingUsers.size() == 1) {
            return Optional.of(matchingUsers.getFirst());
        }

        // Smart disambiguation: prioritize user who actually has held jobs waiting to be printed!
        try {
            List<PrintJob> heldJobs = printJobRepository.findByStatus(JobStatus.HELD);
            for (UserEntity candidate : matchingUsers) {
                boolean hasHeldJobs = heldJobs.stream().anyMatch(j ->
                        (j.getUserSid() != null && j.getUserSid().equalsIgnoreCase(candidate.getUserSid())) ||
                        (j.getUsername() != null && j.getUsername().equalsIgnoreCase(candidate.getUsername())) ||
                        (j.getUser() != null && j.getUser().getId().equals(candidate.getId()))
                );
                if (hasHeldJobs) {
                    LOGGER.info("[PIN AUTH] Multiple users match PIN " + cleanPin + ". Prioritized candidate '" + candidate.getUsername() + "' with held jobs.");
                    return Optional.of(candidate);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[PIN AUTH] Disambiguation error: " + e.getMessage());
        }

        return Optional.of(matchingUsers.getFirst());
    }

    public Optional<UserEntity> authenticateByUsernameAndPin(String username, String pin) {
        if (username == null || username.isBlank() || pin == null || pin.isBlank()) {
            return Optional.empty();
        }
        String cleanUsername = username.trim();
        if (cleanUsername.contains("\\")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('\\') + 1);
        } else if (cleanUsername.contains("/")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('/') + 1);
        } else if (cleanUsername.contains("@")) {
            cleanUsername = cleanUsername.substring(0, cleanUsername.indexOf('@'));
        }
        String cleanPin = pin.trim();
        return userRepository.findByUsername(cleanUsername.toLowerCase().trim())
                .filter(UserEntity::isActive)
                .filter(u -> u.getPinHash() != null && (cleanPin.equals(u.getPinHash()) || passwordEncoder.matches(cleanPin, u.getPinHash())));
    }

    public Optional<UserEntity> authenticateByBadge(String badgeId) {
        if (badgeId == null || badgeId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByBadgeId(badgeId.trim())
                .filter(UserEntity::isActive);
    }

    private Optional<UserEntity> authenticateLdap(String username, String password) {
        DirectoryConfigEntity config = getOrCreateConfig();
        String domain = config.getDomainName();
        if (domain == null || domain.isBlank()) {
            domain = "qoa.local";
        }
        String ldapUrl = config.getLdapUrl();
        if (ldapUrl == null || ldapUrl.isBlank()) {
            ldapUrl = "ldap://127.0.0.1:389";
        }

        String cleanUsername = username.trim();
        if (cleanUsername.contains("\\")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('\\') + 1);
        } else if (cleanUsername.contains("/")) {
            cleanUsername = cleanUsername.substring(cleanUsername.lastIndexOf('/') + 1);
        } else if (cleanUsername.contains("@")) {
            cleanUsername = cleanUsername.substring(0, cleanUsername.indexOf('@'));
        }

        String netbiosDomain = domain.contains(".") ? domain.substring(0, domain.indexOf('.')) : domain;
        String[] principalCandidates = new String[]{
                cleanUsername + "@" + domain,
                netbiosDomain + "\\" + cleanUsername,
                cleanUsername
        };

        DirContext ctx = null;
        for (String principal : principalCandidates) {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, ldapUrl);
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, principal);
            env.put(Context.SECURITY_CREDENTIALS, password);
            env.put("java.naming.ldap.attributes.binary", "objectSid");

            try {
                ctx = new InitialDirContext(env);
                LOGGER.info("[LDAP AUTH] Successfully bound to LDAP using principal: " + principal);
                break;
            } catch (Exception e) {
                LOGGER.fine("[LDAP AUTH] Failed bind with principal " + principal + ": " + e.getMessage());
            }
        }

        if (ctx != null) {
            try {
                SearchControls controls = new SearchControls();
                controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                controls.setReturningAttributes(new String[]{"sAMAccountName", "displayName", "mail", "department", "objectSid"});

                String searchFilter = "(&(objectClass=user)(sAMAccountName=" + sanitizeLdapInput(cleanUsername) + "))";
                NamingEnumeration<SearchResult> answer = ctx.search(config.getBaseDn(), searchFilter, controls);

                if (answer.hasMore()) {
                    SearchResult result = answer.next();
                    Attributes attrs = result.getAttributes();

                    String sAMAccountName = getAttr(attrs, "sAMAccountName");
                    String displayName = getAttr(attrs, "displayName");
                    String mail = getAttr(attrs, "mail");
                    String dept = getAttr(attrs, "department");
                    byte[] sidBytes = attrs.get("objectSid") != null ? (byte[]) attrs.get("objectSid").get() : null;
                    String stringSid = sidBytes != null ? convertBinarySidToString(sidBytes) : null;

                    Optional<UserEntity> existing = Optional.empty();
                    if (stringSid != null) {
                        existing = userRepository.findByUserSid(stringSid);
                    }
                    if (existing.isEmpty() && sAMAccountName != null) {
                        existing = userRepository.findByUsername(sAMAccountName.toLowerCase());
                    }

                    if (existing.isPresent()) {
                        UserEntity u = existing.get();
                        if ((u.getUserSid() == null || u.getUserSid().isBlank()) && stringSid != null) {
                            u.setUserSid(stringSid);
                            userRepository.save(u);
                        }
                        return Optional.of(u);
                    }

                    UserEntity newUser = new UserEntity(sAMAccountName, displayName, stringSid, config.getDefaultPin(), null, mail, dept, "USER", "ACTIVE_DIRECTORY");
                    return Optional.of(userRepository.save(newUser));
                }
            } catch (Exception e) {
                LOGGER.warning("[LDAP SEARCH] Error querying user attributes for " + cleanUsername + ": " + e.getMessage());
            } finally {
                try { ctx.close(); } catch (Exception ignored) {}
            }
        }
        return Optional.empty();
    }

    /* =========================================================================
       7. HELPER METHODS & SYSTEM INTEGRATION BRIDGES
       ========================================================================= */

    private List<Map<String, String>> queryActiveDirectoryViaBridge(String domain, String baseDn, int maxLimit) throws Exception {
        String script = "$s = New-Object System.DirectoryServices.DirectorySearcher([ADSI]'LDAP://127.0.0.1/" + baseDn + "'); " +
                "$s.Filter = '(&(objectCategory=person)(objectClass=user))'; " +
                "$s.SizeLimit = " + maxLimit + "; " +
                "$results = @(); " +
                "foreach ($item in $s.FindAll()) { " +
                "  $p = $item.Properties; " +
                "  $sam = if ($p.samaccountname) { $p.samaccountname[0] } else { '' }; " +
                "  if (-not $sam -or $sam.EndsWith('$')) { continue; } " +
                "  $disp = if ($p.displayname) { $p.displayname[0] } else { $sam }; " +
                "  $mail = if ($p.mail) { $p.mail[0] } else { '' }; " +
                "  $dept = if ($p.department) { $p.department[0] } else { 'Corporate' }; " +
                "  $sid = if ($p.objectsid) { (New-Object System.Security.Principal.SecurityIdentifier($p.objectsid[0], 0)).Value } else { '' }; " +
                "  $results += [PSCustomObject]@{ samAccountName = $sam; displayName = $disp; mail = $mail; department = $dept; sid = $sid }; " +
                "} " +
                "$results | ConvertTo-Json -Compress";

        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script);
        Process process = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        process.waitFor();

        String json = sb.toString().trim();
        if (json.isEmpty() || json.equals("null")) {
            return List.of();
        }

        List<Map<String, String>> list = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        if (root.isArray()) {
            for (JsonNode n : root) {
                list.add(parseUserNode(n));
            }
        } else if (root.isObject()) {
            list.add(parseUserNode(root));
        }
        return list;
    }

    private List<Map<String, String>> queryLocalWindowsUsers() throws Exception {
        String script = "Get-LocalUser | Where-Object { $_.Enabled -and -not $_.Name.EndsWith('$') } | " +
                "ForEach-Object { [PSCustomObject]@{ name = $_.Name; fullName = $_.FullName; sid = $_.SID.Value } } | " +
                "ConvertTo-Json -Compress";

        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script);
        Process process = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        process.waitFor();

        String json = sb.toString().trim();
        if (json.isEmpty() || json.equals("null")) {
            return List.of();
        }

        List<Map<String, String>> list = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        if (root.isArray()) {
            for (JsonNode n : root) {
                Map<String, String> m = new HashMap<>();
                m.put("name", n.has("name") ? n.get("name").asText() : "");
                m.put("fullName", n.has("fullName") ? n.get("fullName").asText() : "");
                m.put("sid", n.has("sid") ? n.get("sid").asText() : "");
                list.add(m);
            }
        } else if (root.isObject()) {
            Map<String, String> m = new HashMap<>();
            m.put("name", root.has("name") ? root.get("name").asText() : "");
            m.put("fullName", root.has("fullName") ? root.get("fullName").asText() : "");
            m.put("sid", root.has("sid") ? root.get("sid").asText() : "");
            list.add(m);
        }
        return list;
    }

    private Map<String, String> parseUserNode(JsonNode n) {
        Map<String, String> m = new HashMap<>();
        m.put("samAccountName", n.has("samAccountName") ? n.get("samAccountName").asText() : "");
        m.put("displayName", n.has("displayName") ? n.get("displayName").asText() : "");
        m.put("mail", n.has("mail") ? n.get("mail").asText() : "");
        m.put("department", n.has("department") ? n.get("department").asText() : "");
        m.put("sid", n.has("sid") ? n.get("sid").asText() : "");
        return m;
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
                .replace("\u0000", "\\00");
    }

    public static String convertBinarySidToString(byte[] sid) {
        if (sid == null || sid.length < 8) {
            return "S-1-0-0";
        }
        StringBuilder sb = new StringBuilder("S-");
        sb.append(sid[0] & 0xFF).append("-");

        long authority = 0;
        for (int i = 2; i <= 7; i++) {
            authority = (authority << 8) + (sid[i] & 0xFF);
        }
        sb.append(authority);

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
