package com.rr.secureprint.controller;

import com.rr.secureprint.dto.AuthResponse;
import com.rr.secureprint.dto.LoginRequest;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        if (response.authenticated()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/current")
    public ResponseEntity<AuthResponse> getCurrentUser(HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            return ResponseEntity.ok(AuthResponse.success(
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getUserSid(),
                    user.getDepartment(),
                    user.getRole()
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
