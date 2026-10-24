package com.rr.secureprint.service;

import com.rr.secureprint.dto.AuthResponse;
import com.rr.secureprint.dto.LoginRequest;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    public static final String SESSION_USER_KEY = "RR_SECURE_PRINT_USER";

    private final ActiveDirectoryService activeDirectoryService;
    private final AuditService auditService;
    private final CopyTrackingService copyTrackingService;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final ToshibaEwbService toshibaEwbService;

    // Rate Limiting: Max 5 failed attempts per IP within 60 seconds
    private final Map<String, RateLimitTracker> rateLimitMap = new ConcurrentHashMap<>();

    public AuthService(ActiveDirectoryService activeDirectoryService,
                       AuditService auditService,
                       CopyTrackingService copyTrackingService,
                       PrinterRepository printerRepository,
                       UserRepository userRepository,
                       ToshibaEwbService toshibaEwbService) {
        this.activeDirectoryService = activeDirectoryService;
        this.auditService = auditService;
        this.copyTrackingService = copyTrackingService;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.toshibaEwbService = toshibaEwbService;
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);

        // Rate limiting check
        if (isRateLimited(clientIp)) {
            auditService.logEvent("LOGIN_RATE_LIMITED", null, request.username(), null, null, clientIp,
                    "Too many failed login attempts", "FAILURE");
            return AuthResponse.failure("Too many failed attempts. Please wait 60 seconds before trying again.");
        }

        Optional<UserEntity> userOpt = Optional.empty();
        boolean isCardSelfAssociation = false;

        // 1. PaperCut Self-Association: User tapped an unregistered badge and provided PIN or credentials to link it
        if (request.hasBadge() && (request.hasPin() || request.hasCredentials())) {
            if (request.hasCredentials()) {
                userOpt = activeDirectoryService.authenticate(request.username(), request.password());
            } else {
                if (request.username() != null && !request.username().isBlank()) {
                    userOpt = activeDirectoryService.authenticateByUsernameAndPin(request.username(), request.pin());
                } else {
                    userOpt = activeDirectoryService.authenticateByPin(request.pin());
                }
            }

            if (userOpt.isPresent()) {
                UserEntity user = userOpt.get();
                String cleanBadge = request.badgeId().trim();

                // Ensure this badge is not already assigned to another active user
                Optional<UserEntity> existingBadgeUser = userRepository.findByAnyBadgeId(cleanBadge);
                if (existingBadgeUser.isPresent() && !existingBadgeUser.get().getId().equals(user.getId())) {
                    auditService.logEvent("CARD_SELF_ASSOCIATION_CONFLICT", user.getUserSid(), user.getUsername(), null, null, clientIp,
                            "Card " + cleanBadge + " is already linked to " + existingBadgeUser.get().getUsername(), "FAILURE");
                    return AuthResponse.failure("This card is already linked to user '" + existingBadgeUser.get().getUsername() + "'");
                }

                // Associate card with user: primary (RFID) or secondary (NFC)
                if (user.getBadgeId() == null || user.getBadgeId().isBlank()) {
                    user.setBadgeId(cleanBadge);
                } else if (!cleanBadge.equalsIgnoreCase(user.getBadgeId())) {
                    user.setSecondaryBadgeId(cleanBadge);
                }
                user = userRepository.save(user);
                userOpt = Optional.of(user);
                isCardSelfAssociation = true;

                auditService.logEvent("CARD_SELF_ASSOCIATION_SUCCESS", user.getUserSid(), user.getUsername(), null, null, clientIp,
                        "RFID/NFC card [" + cleanBadge + "] successfully registered to user " + user.getUsername(), "SUCCESS");
            }
        } else if (request.hasCredentials()) {
            userOpt = activeDirectoryService.authenticate(request.username(), request.password());
        } else if (request.hasPin()) {
            if (request.username() != null && !request.username().isBlank()) {
                userOpt = activeDirectoryService.authenticateByUsernameAndPin(request.username(), request.pin());
            } else {
                userOpt = activeDirectoryService.authenticateByPin(request.pin());
            }
        } else if (request.hasBadge()) {
            // Pure Card Tap (Card Swipe)
            String cleanBadge = request.badgeId().trim();
            userOpt = activeDirectoryService.authenticateByBadge(cleanBadge);
            if (userOpt.isEmpty()) {
                // Unknown Card -> Trigger Self-Association prompt (PaperCut MF workflow)
                auditService.logEvent("CARD_UNKNOWN_TAP", null, null, null, null, clientIp,
                        "Unregistered RFID/NFC card tapped: " + cleanBadge, "INFO");
                return AuthResponse.unregisteredCard(cleanBadge, "Card not registered. Enter your PIN or account credentials to link this card.");
            }
        }

        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            // Clear rate limit on success
            rateLimitMap.remove(clientIp);

            // Create new session to prevent session fixation
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(SESSION_USER_KEY, user);
            session.setMaxInactiveInterval(300); // 5 minutes inactivity timeout for MFP touchscreen

            auditService.logEvent("LOGIN_SUCCESS", user.getUserSid(), user.getUsername(), null, null, clientIp,
                    "Authenticated via " + (isCardSelfAssociation ? "CARD_SELF_ASSOCIATION" : request.hasBadge() ? "RFID_CARD" : request.hasPin() ? "PIN" : "CREDENTIALS"), "SUCCESS");

            // Register terminal session for attribution
            copyTrackingService.registerActiveTerminalUser(clientIp, user.getUsername());
            if (request.printerIp() != null && !request.printerIp().isBlank()) {
                copyTrackingService.registerActiveTerminalUser(request.printerIp(), user.getUsername());
            }
            if (request.printerCode() != null && !request.printerCode().isBlank()) {
                printerRepository.findByPrinterCode(request.printerCode().trim().toUpperCase())
                        .ifPresent(p -> copyTrackingService.registerActiveTerminalUser(p.getIpAddress(), user.getUsername()));
            }

            return AuthResponse.success(user.getUsername(), user.getDisplayName(), user.getUserSid(),
                    user.getDepartment(), user.getRole(), user.getPrintCount(), user.getCopyCount());
        } else {
            recordFailedAttempt(clientIp);
            auditService.logEvent("LOGIN_FAILED", null, request.username(), null, null, clientIp,
                    "Invalid PIN or credentials provided", "FAILURE");
            return AuthResponse.failure("Invalid credentials or PIN code.");
        }
    }

    public void logout(HttpServletRequest httpRequest) {
        logout(httpRequest, null, null);
    }

    public void logout(HttpServletRequest httpRequest, String printerIp, String printerCode) {
        HttpSession session = httpRequest.getSession(false);
        String clientIp = getClientIp(httpRequest);
        java.util.Set<String> targetIps = new java.util.LinkedHashSet<>();
        if (clientIp != null && !clientIp.isBlank()) targetIps.add(clientIp.trim());

        if (session != null) {
            String sessPrinterIp = (String) session.getAttribute("RR_ACTIVE_PRINTER_IP");
            if (sessPrinterIp != null && !sessPrinterIp.isBlank()) targetIps.add(sessPrinterIp.trim());

            UserEntity user = (UserEntity) session.getAttribute(SESSION_USER_KEY);
            if (user != null) {
                auditService.logEvent("LOGOUT", user.getUserSid(), user.getUsername(), null, null, clientIp,
                        "User logged out", "SUCCESS");
            }
            session.invalidate();
        }

        if (printerIp != null && !printerIp.isBlank()) targetIps.add(printerIp.trim());
        if (printerCode != null && !printerCode.isBlank()) {
            printerRepository.findByPrinterCode(printerCode.trim().toUpperCase())
                    .ifPresent(p -> targetIps.add(p.getIpAddress()));
        }

        for (String ip : targetIps) {
            copyTrackingService.recordTerminalLogout(ip);
        }
    }

    public Optional<UserEntity> getCurrentUser(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            Object obj = session.getAttribute(SESSION_USER_KEY);
            if (obj instanceof UserEntity user) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    private boolean isRateLimited(String ip) {
        RateLimitTracker tracker = rateLimitMap.get(ip);
        if (tracker == null) return false;
        if (Instant.now().getEpochSecond() - tracker.lastAttemptEpoch > 60) {
            rateLimitMap.remove(ip);
            return false;
        }
        return tracker.failedCount >= 5;
    }

    private void recordFailedAttempt(String ip) {
        rateLimitMap.compute(ip, (key, tracker) -> {
            long now = Instant.now().getEpochSecond();
            if (tracker == null || now - tracker.lastAttemptEpoch > 60) {
                return new RateLimitTracker(1, now);
            } else {
                return new RateLimitTracker(tracker.failedCount + 1, now);
            }
        });
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RateLimitTracker(int failedCount, long lastAttemptEpoch) {}
}
