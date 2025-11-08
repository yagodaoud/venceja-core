package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.CategoriaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório para categorias
 */
@Repository
public interface CategoriaRepository extends JpaRepository<CategoriaEntity, Long> {

    Page<CategoriaEntity> findByUserId(Long userId, Pageable pageable);
}
