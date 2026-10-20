package com.rr.secureprint.service;

import com.rr.secureprint.dto.UserDto;
import com.rr.secureprint.dto.UserSaveRequest;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDto::fromEntity)
                .toList();
    }

    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id).map(UserDto::fromEntity);
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

        UserEntity user = new UserEntity(
                username,
                request.displayName() != null ? request.displayName().trim() : username,
                sid,
                pinHash,
                request.badgeId() != null && !request.badgeId().isBlank() ? request.badgeId().trim() : null,
                request.email() != null ? request.email().trim() : null,
                request.department() != null ? request.department().trim() : "General",
                request.role() != null ? request.role().toUpperCase().trim() : "USER"
        );

        if (request.isActive() != null) {
            user.setActive(request.isActive());
        }

        UserEntity saved = userRepository.save(user);

        auditService.logEvent("USER_CREATED", saved.getUserSid(), saved.getUsername(), null, null, clientIp,
                "User created by " + adminActor + " (Role: " + saved.getRole() + ", Dept: " + saved.getDepartment() + ")", "SUCCESS");

        return UserDto.fromEntity(saved);
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
        if (request.pin() != null && !request.pin().isBlank()) {
            user.setPinHash(request.pin().trim());
        }
        if (request.isActive() != null) {
            user.setActive(request.isActive());
        }
        user.setUpdatedAt(Instant.now());

        UserEntity updated = userRepository.save(user);

        auditService.logEvent("USER_UPDATED", updated.getUserSid(), updated.getUsername(), null, null, clientIp,
                "User updated by " + adminActor, "SUCCESS");

        return UserDto.fromEntity(updated);
    }

    @Transactional
    public void resetPin(Long id, String newPin, String adminActor, String clientIp) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + id + " not found."));
        user.setPinHash(newPin.trim());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditService.logEvent("USER_PIN_RESET", user.getUserSid(), user.getUsername(), null, null, clientIp,
                "PIN reset performed by " + adminActor, "SUCCESS");
    }

    @Transactional
    public void deleteUser(Long id, String adminActor, String clientIp) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + id + " not found."));
        
        userRepository.delete(user);

        auditService.logEvent("USER_DELETED", user.getUserSid(), user.getUsername(), null, null, clientIp,
                "User deleted by " + adminActor, "WARNING");
    }
}
