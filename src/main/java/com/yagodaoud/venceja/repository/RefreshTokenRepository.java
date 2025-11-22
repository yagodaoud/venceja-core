```
package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.RefreshTokenEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para refresh tokens
 */
@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepository<RefreshTokenEntity> {

    public Optional<RefreshTokenEntity> findByToken(String token) {
        return find("token", token).firstResultOptional();
    }

    public List<RefreshTokenEntity> findByUser(UserEntity user) {
        return find("user", user).list();
    }

    public List<RefreshTokenEntity> findActiveTokensByUserId(Long userId, LocalDateTime now) {
        return find("user.id = ?1 and revoked = false and expiresAt > ?2", userId, now).list();
    }

    public void deleteExpiredTokens(LocalDateTime now) {
        delete("expiresAt < ?1", now);
    }

    public void revokeAllUserTokens(Long userId) {
        update("revoked = true where user.id = ?1", userId);
    }

    public void deleteAllByUserId(Long userId) {
        delete("user.id = ?1", userId);
    }
}
```