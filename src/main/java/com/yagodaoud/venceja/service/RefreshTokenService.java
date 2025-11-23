package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.entity.RefreshTokenEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.RefreshTokenRepository;
import com.yagodaoud.venceja.repository.UserRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Serviço para gerenciamento de refresh tokens
 */
@Slf4j
@ApplicationScoped
public class RefreshTokenService {

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    UserRepository userRepository;

    @ConfigProperty(name = "jwt.refresh-token.expiration", defaultValue = "2592000000")
    Long refreshTokenDurationMs;

    @ConfigProperty(name = "jwt.refresh-token.max-per-user", defaultValue = "5")
    Integer maxTokensPerUser;

    /**
     * Cria um novo refresh token para o usuário
     */
    @Transactional
    public RefreshTokenEntity createRefreshToken(String userEmail, String deviceInfo) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Remove tokens expirados do usuário
        cleanupExpiredTokens(user.getId());

        // Verifica se usuário já tem muitos tokens ativos
        List<RefreshTokenEntity> activeTokens = refreshTokenRepository
                .findActiveTokensByUserId(user.getId(), LocalDateTime.now());

        // Se ultrapassar o limite, revoga o mais antigo
        if (activeTokens.size() >= maxTokensPerUser) {
            RefreshTokenEntity oldestToken = activeTokens.stream()
                    .min((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                    .orElse(null);

            if (oldestToken != null) {
                oldestToken.setRevoked(true);
                // Entity is managed, no need to save
                log.info("Token antigo revogado para usuário: {}", userEmail);
            }
        }

        // Cria novo refresh token
        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenDurationMs / 1000))
                .deviceInfo(deviceInfo)
                .revoked(false)
                .build();

        refreshTokenRepository.persist(refreshToken);
        log.info("Refresh token criado para usuário: {}", userEmail);

        return refreshToken;
    }

    /**
     * Valida e retorna o refresh token
     */
    @Transactional // readOnly not supported in Jakarta Transactional directly, but default is required
    public RefreshTokenEntity validateRefreshToken(String token) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido"));

        if (refreshToken.isExpired()) {
            log.warn("Refresh token expirado: {}", token);
            throw new IllegalArgumentException("Refresh token expirado");
        }

        if (refreshToken.getRevoked()) {
            log.warn("Refresh token revogado: {}", token);
            throw new IllegalArgumentException("Refresh token revogado");
        }

        return refreshToken;
    }

    /**
     * Revoga um refresh token específico
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token não encontrado"));

        refreshToken.setRevoked(true);
        // Entity is managed
        log.info("Refresh token revogado");
    }

    /**
     * Revoga todos os refresh tokens de um usuário (logout de todos os dispositivos)
     */
    @Transactional
    public void revokeAllUserTokens(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        refreshTokenRepository.revokeAllUserTokens(user.getId());
        log.info("Todos os tokens revogados para usuário: {}", userEmail);
    }

    /**
     * Remove tokens expirados do usuário
     */
    @Transactional
    public void cleanupExpiredTokens(Long userId) {
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findByUser(
                userRepository.findByIdOptional(userId)
                        .orElseThrow(() -> new RuntimeException("Usuário não encontrado"))
        );

        tokens.stream()
                .filter(RefreshTokenEntity::isExpired)
                .forEach(refreshTokenRepository::delete);
    }

    /**
     * Job agendado para limpar tokens expirados (executa diariamente às 3h)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokensScheduled() {
        log.info("Iniciando limpeza de refresh tokens expirados...");
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Limpeza de refresh tokens concluída");
    }

    /**
     * Lista tokens ativos do usuário
     */
    @Transactional
    public List<RefreshTokenEntity> getActiveTokens(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        return refreshTokenRepository.findActiveTokensByUserId(user.getId(), LocalDateTime.now());
    }
}