package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.CategoriaEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repositório para categorias
 */
@ApplicationScoped
public class CategoriaRepository implements PanacheRepository<CategoriaEntity> {

    public List<CategoriaEntity> findByUserId(Long userId, int pageIndex, int pageSize) {
        return find("user.id", userId).page(pageIndex, pageSize).list();
    }

    public long countByUserId(Long userId) {
        return count("user.id", userId);
    }
}
