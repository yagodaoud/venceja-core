package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.entity.BoletoStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repositório para boletos
 */
@ApplicationScoped
public class BoletoRepository implements PanacheRepository<BoletoEntity> {

    public List<BoletoEntity> findByUserIdWithFilters(
            Long userId,
            List<BoletoStatus> statuses,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pageIndex,
            int pageSize,
            String sortBy,
            String direction) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("statuses", (statuses == null || statuses.isEmpty()) ? null : statuses);
        params.put("dataInicio", dataInicio);
        params.put("dataFim", dataFim);

        io.quarkus.panache.common.Sort sort = io.quarkus.panache.common.Sort.by(sortBy);
        if ("desc".equalsIgnoreCase(direction)) {
            sort.descending();
        } else {
            sort.ascending();
        }

        return find("""
            SELECT b FROM BoletoEntity b
            LEFT JOIN FETCH b.categoria
            WHERE b.user.id = :userId
              AND (:statuses IS NULL OR b.status IN :statuses)
              AND (:dataInicio IS NULL OR :dataInicio <= b.vencimento)
              AND (:dataFim IS NULL OR :dataFim >= b.vencimento)
            """, sort, params).page(pageIndex, pageSize).list();
    }

    public long countByUserIdWithFilters(
            Long userId,
            List<BoletoStatus> statuses,
            LocalDate dataInicio,
            LocalDate dataFim) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("statuses", (statuses == null || statuses.isEmpty()) ? null : statuses);
        params.put("dataInicio", dataInicio);
        params.put("dataFim", dataFim);

        return count("""
            FROM BoletoEntity b
            WHERE b.user.id = :userId
              AND (:statuses IS NULL OR b.status IN :statuses)
              AND (:dataInicio IS NULL OR :dataInicio <= b.vencimento)
              AND (:dataFim IS NULL OR :dataFim >= b.vencimento)
            """, params);
    }

    public List<BoletoEntity> findOverdueBoletosByUserId(Long userId) {
        return find("""
            SELECT b FROM BoletoEntity b
            LEFT JOIN FETCH b.categoria
            WHERE b.user.id = ?1
              AND b.status = 'PENDENTE'
              AND b.vencimento < CURRENT_DATE
            """, userId).list();
    }

    public List<BoletoEntity> findPendingBoletosNearDueDate(LocalDate minDate, LocalDate maxDate) {
        return find("""
            SELECT b FROM BoletoEntity b
            LEFT JOIN FETCH b.categoria
            WHERE b.status = 'PENDENTE'
              AND b.vencimento <= ?1
              AND b.vencimento >= ?2
            """, maxDate, minDate).list();
    }
}