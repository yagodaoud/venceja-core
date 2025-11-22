package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.config.JwtService;
import com.yagodaoud.venceja.dto.LoginRequest;
import com.yagodaoud.venceja.dto.RefreshTokenRequest;
import com.yagodaoud.venceja.dto.RefreshTokenResponse;
import com.yagodaoud.venceja.entity.RefreshTokenEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço de autenticação com suporte a refresh tokens
 */
@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    JwtService jwtService;

    @Inject
    RefreshTokenService refreshTokenService;

    @ConfigProperty(name = "jwt.expiration", defaultValue = "86400000")
    Long jwtExpirationMs;

    /**
     * Login com geração de access token e refresh token
     */
    @Transactional
    public Map<String, Object> login(LoginRequest request) {
        return login(request, null);
    }

    /**
     * Login com informações do dispositivo
     */
    @Transactional
    public Map<String, Object> login(LoginRequest request, String deviceInfo) {
        // Busca usuário
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado")); // Using RuntimeException or custom exception

        // Verifica senha
        if (!BcryptUtil.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Credenciais inválidas");
        }

        // Gera access token (JWT)
        String accessToken = jwtService.generateToken(request.getEmail());

        // Gera refresh token
        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(
                request.getEmail(),
                deviceInfo
        );

        // Monta resposta
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("nome", user.getNome());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken.getToken());
        response.put("tokenType", "Bearer");
        response.put("expiresIn", jwtExpirationMs / 1000); // em segundos
        response.put("user", userData);

        return response;
    }

    /**
     * Renova o access token usando o refresh token
     */
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        // Valida o refresh token
        RefreshTokenEntity refreshToken = refreshTokenService.validateRefreshToken(
                request.getRefreshToken()
        );

        // Gera novo access token
        String newAccessToken = jwtService.generateToken(refreshToken.getUser().getEmail());

        // Opcional: Rotação de refresh token (gera novo refresh token e revoga o antigo)
        RefreshTokenEntity newRefreshToken = refreshTokenService.createRefreshToken(
                refreshToken.getUser().getEmail(),
                request.getDeviceInfo()
        );

        // Revoga o refresh token antigo
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .build();
    }

    /**
     * Logout - revoga o refresh token
     */
    @Transactional
    public void logout(String refreshToken) {
        try {
            refreshTokenService.revokeRefreshToken(refreshToken);
        } catch (IllegalArgumentException e) {
            // Token já inválido ou não existe, ignore
        }
    }

    /**
     * Logout de todos os dispositivos - revoga todos os refresh tokens do usuário
     */
    @Transactional
    public void logoutAllDevices(String userEmail) {
        refreshTokenService.revokeAllUserTokens(userEmail);
    }
}