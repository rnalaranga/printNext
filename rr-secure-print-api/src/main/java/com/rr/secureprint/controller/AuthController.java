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
        } else if (response.unregisteredCard()) {
            // Return 200 OK so client receives card info cleanly for Self-Association without a generic 401 error
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest httpRequest) {
        String printerIp = (body != null) ? body.get("printerIp") : null;
        String printerCode = (body != null) ? body.get("printerCode") : null;
        authService.logout(httpRequest, printerIp, printerCode);
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
                    user.getRole(),
                    user.getPrintCount(),
                    user.getCopyCount()
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
