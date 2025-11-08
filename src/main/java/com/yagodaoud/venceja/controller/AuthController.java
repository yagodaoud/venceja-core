package com.yagodaoud.venceja.controller;

import com.yagodaoud.venceja.dto.ApiResponse;
import com.yagodaoud.venceja.dto.LoginRequest;
import com.yagodaoud.venceja.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller para autenticação
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> data = authService.login(request);

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .data(data)
                .message("Login realizado com sucesso")
                .build();

        return ResponseEntity.ok(response);
    }
}
