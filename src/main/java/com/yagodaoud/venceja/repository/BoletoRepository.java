package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.entity.BoletoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositório para boletos - OTIMIZADO com JOIN FETCH
 */
@Repository
public interface BoletoRepository extends JpaRepository<BoletoEntity, Long> {

    @Query("""
        SELECT b FROM BoletoEntity b
        LEFT JOIN FETCH b.categoria
        WHERE b.user.id = :userId
          AND (:statuses IS NULL OR b.status IN :statuses)
          AND (COALESCE(:dataInicio, b.vencimento) <= b.vencimento)
          AND (COALESCE(:dataFim, b.vencimento) >= b.vencimento)
        ORDER BY b.vencimento ASC
        """)
    Page<BoletoEntity> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("statuses") List<BoletoStatus> statuses, // CHANGE: List parameter
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            Pageable pageable);

    /**
     * Busca boletos vencidos - também com JOIN FETCH
     */
    @Query("""
        SELECT b FROM BoletoEntity b
        LEFT JOIN FETCH b.categoria
        WHERE b.user.id = :userId
          AND b.status = 'PENDENTE'
          AND b.vencimento < CURRENT_DATE
        """)
    List<BoletoEntity> findOverdueBoletosByUserId(@Param("userId") Long userId);

    /**
     * Busca boletos próximos do vencimento para alertas
     */
    @Query("""
        SELECT b FROM BoletoEntity b
        LEFT JOIN FETCH b.categoria
        WHERE b.status = 'PENDENTE'
          AND b.vencimento <= :maxDate
          AND b.vencimento >= :minDate
        """)
    List<BoletoEntity> findPendingBoletosNearDueDate(
            @Param("minDate") LocalDate minDate,
            @Param("maxDate") LocalDate maxDate);
}