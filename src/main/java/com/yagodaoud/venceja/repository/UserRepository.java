package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Repositório para usuários
 */
@ApplicationScoped
public class UserRepository implements PanacheRepository<UserEntity> {

    public Optional<UserEntity> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }
}
