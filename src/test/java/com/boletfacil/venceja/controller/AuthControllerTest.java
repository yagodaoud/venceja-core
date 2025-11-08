package com.yagodaoud.venceja.controller;

import com.yagodaoud.venceja.dto.LoginRequest;
import com.yagodaoud.venceja.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes do AuthController
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@venceja.com");
        request.setPassword("password123");

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("token", "test-token");
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", 1L);
        userData.put("email", "admin@venceja.com");
        userData.put("nome", "Admin");
        responseData.put("user", userData);

        when(authService.login(any(LoginRequest.class))).thenReturn(responseData);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.message").value("Login realizado com sucesso"));
    }
}
