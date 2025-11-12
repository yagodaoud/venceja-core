package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.entity.RefreshTokenEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.RefreshTokenRepository;
import com.yagodaoud.venceja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Serviço para gerenciamento de refresh tokens
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-token.expiration:2592000000}") // 30 dias em ms
    private Long refreshTokenDurationMs;

    @Value("${jwt.refresh-token.max-per-user:5}") // Máximo de tokens ativos por usuário
    private Integer maxTokensPerUser;

    /**
     * Cria um novo refresh token para o usuário
     */
    @Transactional
    public RefreshTokenEntity createRefreshToken(String userEmail, String deviceInfo) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

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
                refreshTokenRepository.save(oldestToken);
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

        refreshToken = refreshTokenRepository.save(refreshToken);
        log.info("Refresh token criado para usuário: {}", userEmail);

        return refreshToken;
    }

    /**
     * Valida e retorna o refresh token
     */
    @Transactional(readOnly = true)
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
        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token revogado");
    }

    /**
     * Revoga todos os refresh tokens de um usuário (logout de todos os dispositivos)
     */
    @Transactional
    public void revokeAllUserTokens(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        refreshTokenRepository.revokeAllUserTokens(user.getId());
        log.info("Todos os tokens revogados para usuário: {}", userEmail);
    }

    /**
     * Remove tokens expirados do usuário
     */
    @Transactional
    public void cleanupExpiredTokens(Long userId) {
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findByUser(
                userRepository.findById(userId)
                        .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"))
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
    @Transactional(readOnly = true)
    public List<RefreshTokenEntity> getActiveTokens(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        return refreshTokenRepository.findActiveTokensByUserId(user.getId(), LocalDateTime.now());
    }
}