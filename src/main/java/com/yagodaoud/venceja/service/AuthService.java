package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.config.JwtService;
import com.yagodaoud.venceja.dto.LoginRequest;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço de autenticação
 */
@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;

        public Map<String, Object> login(LoginRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));

                UserEntity user = userRepository.findByEmail(request.getEmail())
                                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

                String token = jwtService.generateToken(request.getEmail());

                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.getId());
                userData.put("email", user.getEmail());
                userData.put("nome", user.getNome());

                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("user", userData);

                return response;
        }
}
