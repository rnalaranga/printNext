package com.rr.secureprint.service;

import com.rr.secureprint.dto.UserDto;
import com.rr.secureprint.dto.UserSaveRequest;
import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PrintJobRepository printJobRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PrintJobRepository printJobRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.printJobRepository = printJobRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UserDto> getAllUsers() {
        List<PrintJob> heldJobs = printJobRepository.findByStatus(JobStatus.HELD);
        Map<Long, Long> heldCountByUser = heldJobs.stream()
                .filter(j -> j.getUser() != null && j.getUser().getId() != null)
                .collect(Collectors.groupingBy(j -> j.getUser().getId(), Collectors.counting()));

        return userRepository.findAll().stream()
                .map(u -> {
                    int count = heldCountByUser.getOrDefault(u.getId(), 0L).intValue();
                    return UserDto.fromEntity(u, count);
                })
                .toList();
    }

    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id).map(u -> {
            long count = printJobRepository.findByStatus(JobStatus.HELD).stream()
                    .filter(j -> j.getUser() != null && j.getUser().getId() != null && j.getUser().getId().equals(u.getId()))
                    .count();
            return UserDto.fromEntity(u, (int) count);
        });
    }

    @Transactional
    public UserDto createUser(UserSaveRequest request, String adminActor, String clientIp) {
        String username = request.username().toLowerCase().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username '" + username + "' already exists.");
        }

        String sid = request.userSid();
        if (sid == null || sid.isBlank()) {
            sid = "S-1-5-21-" + Math.abs(username.hashCode()) + "-100" + (userRepository.count() + 1);
        }

        String pinHash = null;
        if (request.pin() != null && !request.pin().isBlank()) {
            pinHash = request.pin().trim();
        }

        String authSource = request.authSource() != null && !request.authSource().isBlank()
                ? request.authSource().toUpperCase().trim()
                : "MANUAL";

        UserEntity user = new UserEntity(
                username,
                request.displayName() != null ? request.displayName().trim() : username,
                sid,
                pinHash,
                request.badgeId() != null && !request.badgeId().isBlank() ? request.badgeId().trim() : null,
                request.email() != null ? request.email().trim() : null,
                request.department() != null ? request.department().trim() : "General",
                request.role() != null ? request.role().toUpperCase().trim() : "USER",
                authSource
        );

        if (request.isActive() != null) {
            user.setActive(request.isActive());
        }
        if (request.externalAttribute1() != null) {
            user.setExternalAttribute1(request.externalAttribute1().trim());
        }
        if (request.externalAttribute2() != null) {
            user.setExternalAttribute2(request.externalAttribute2().trim());
        }
        if (request.externalAttribute3() != null) {
            user.setExternalAttribute3(request.externalAttribute3().trim());
        }
        if (request.secondaryBadgeId() != null && !request.secondaryBadgeId().isBlank()) {
            user.setSecondaryBadgeId(request.secondaryBadgeId().trim());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(request.password().trim());
        }
        if (request.externalAttribute1() != null) {
            user.setExternalAttribute1(request.externalAttribute1().trim());
        }
        if (request.externalAttribute2() != null) {
            user.setExternalAttribute2(request.externalAttribute2().trim());
        }
        if (request.externalAttribute3() != null) {
            user.setExternalAttribute3(request.externalAttribute3().trim());
        }

        UserEntity saved = userRepository.save(user);

        auditService.logEvent("USER_CREATED", saved.getUserSid(), saved.getUsername(), null, null, clientIp,
                "User created by " + adminActor + " (Role: " + saved.getRole() + ", Dept: " + saved.getDepartment() + ")", "SUCCESS");

        return UserDto.fromEntity(saved, 0);
    }

    @Transactional
    public UserDto updateUser(Long id, UserSaveRequest request, String adminActor, String clientIp) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + id + " not found."));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.department() != null) {
            user.setDepartment(request.department().trim());
        }
        if (request.role() != null && !request.role().isBlank()) {
            user.setRole(request.role().toUpperCase().trim());
        }
        if (request.email() != null) {
            user.setEmail(request.email().trim());
        }
        if (request.badgeId() != null) {
            user.setBadgeId(request.badgeId().isBlank() ? null : request.badgeId().trim());
        }
        if (request.secondaryBadgeId() != null) {
            user.setSecondaryBadgeId(request.secondaryBadgeId().isBlank() ? null : request.secondaryBadgeId().trim());
        }
        if (request.pin() != null && !request.pin().isBlank()) {
            user.setPinHash(request.pin().trim());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(request.password().trim());
        }
        if (request.authSource() != null && !request.authSource().isBlank()) {
            user.setAuthSource(request.authSource().trim());
        }
        if (request.isActive() != null) {
            user.setActive(request.isActive());
        }
        user.setUpdatedAt(Instant.now());

        UserEntity saved = userRepository.save(user);

        auditService.logEvent("USER_UPDATED", saved.getUserSid(), saved.getUsername(), null, null, clientIp,
                "User updated by " + adminActor, "SUCCESS");

        return UserDto.fromEntity(saved);
    }

    @Transactional
    public void deleteUser(Long id, String adminActor, String clientIp) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + id + " not found."));

        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("Default administrator account cannot be deleted.");
        }

        auditService.logEvent("USER_DELETED", user.getUserSid(), user.getUsername(), null, null, clientIp,
                "User " + user.getUsername() + " deleted by " + adminActor, "WARNING");

        userRepository.delete(user);
    }

    @Transactional
    public void resetPin(Long id, String newPin, String adminActor, String clientIp) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + id + " not found."));

        user.setPinHash(newPin.trim());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditService.logEvent("PIN_RESET", user.getUserSid(), user.getUsername(), null, null, clientIp,
                "PIN reset for user " + user.getUsername() + " by " + adminActor, "SUCCESS");
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, String adminActor, String clientIp) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + id + " not found."));

        user.setPasswordHash(newPassword.trim());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditService.logEvent("PASSWORD_RESET", user.getUserSid(), user.getUsername(), null, null, clientIp,
                "Password reset for user " + user.getUsername() + " by " + adminActor, "SUCCESS");
    }
}
