package com.rr.secureprint.service;

import com.rr.secureprint.dto.AuthResponse;
import com.rr.secureprint.dto.LoginRequest;
import com.rr.secureprint.entity.UserEntity;
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

    // Rate Limiting: Max 5 failed attempts per IP within 60 seconds
    private final Map<String, RateLimitTracker> rateLimitMap = new ConcurrentHashMap<>();

    public AuthService(ActiveDirectoryService activeDirectoryService, AuditService auditService) {
        this.activeDirectoryService = activeDirectoryService;
        this.auditService = auditService;
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

        if (request.hasPin()) {
            if (request.username() != null && !request.username().isBlank()) {
                userOpt = activeDirectoryService.authenticateByUsernameAndPin(request.username(), request.pin());
            } else {
                userOpt = activeDirectoryService.authenticateByPin(request.pin());
            }
        } else if (request.hasBadge()) {
            userOpt = activeDirectoryService.authenticateByBadge(request.badgeId());
        } else if (request.hasCredentials()) {
            userOpt = activeDirectoryService.authenticate(request.username(), request.password());
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
                    "Authenticated via " + (request.hasPin() ? "PIN" : request.hasBadge() ? "BADGE" : "CREDENTIALS"), "SUCCESS");

            return AuthResponse.success(user.getUsername(), user.getDisplayName(), user.getUserSid(),
                    user.getDepartment(), user.getRole());
        } else {
            recordFailedAttempt(clientIp);
            auditService.logEvent("LOGIN_FAILED", null, request.username(), null, null, clientIp,
                    "Invalid PIN or credentials provided", "FAILURE");
            return AuthResponse.failure("Invalid credentials or PIN code.");
        }
    }

    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String clientIp = getClientIp(httpRequest);
        if (session != null) {
            UserEntity user = (UserEntity) session.getAttribute(SESSION_USER_KEY);
            if (user != null) {
                auditService.logEvent("LOGOUT", user.getUserSid(), user.getUsername(), null, null, clientIp,
                        "User logged out", "SUCCESS");
            }
            session.invalidate();
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
