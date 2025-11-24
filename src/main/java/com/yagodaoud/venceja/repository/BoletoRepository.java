package com.yagodaoud.venceja.repository;

import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.entity.BoletoStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

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

        StringBuilder query = new StringBuilder("""
        SELECT b FROM BoletoEntity b
        LEFT JOIN FETCH b.categoria
        WHERE b.user.id = :userId
    """);

        if (statuses != null && !statuses.isEmpty()) {
            query.append(" AND b.status IN :statuses");
            params.put("statuses", statuses);
        }

        if (dataInicio != null) {
            query.append(" AND b.vencimento >= :dataInicio");
            params.put("dataInicio", dataInicio);
        }

        if (dataFim != null) {
            query.append(" AND b.vencimento <= :dataFim");
            params.put("dataFim", dataFim);
        }

        io.quarkus.panache.common.Sort sort = io.quarkus.panache.common.Sort.by("b." + sortBy);
        if ("desc".equalsIgnoreCase(direction)) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        List<BoletoEntity> results = find(query.toString(), sort, params)
                .page(pageIndex, pageSize)
                .list();

        EntityManager em = getEntityManager();
        results.forEach(em::detach);

        return results;
    }

    public long countByUserIdWithFilters(
            Long userId,
            List<BoletoStatus> statuses,
            LocalDate dataInicio,
            LocalDate dataFim) {

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        StringBuilder query = new StringBuilder("SELECT COUNT(b) FROM BoletoEntity b WHERE b.user.id = :userId");

        if (statuses != null && !statuses.isEmpty()) {
            query.append(" AND b.status IN :statuses");
            params.put("statuses", statuses);
        }

        if (dataInicio != null) {
            query.append(" AND b.vencimento >= :dataInicio");
            params.put("dataInicio", dataInicio);
        }

        if (dataFim != null) {
            query.append(" AND b.vencimento <= :dataFim");
            params.put("dataFim", dataFim);
        }

        return count(query.toString(), params);
    }

    public List<BoletoEntity> findOverdueBoletosByUserId(Long userId) {
        List<BoletoEntity> results = find("""
            SELECT b FROM BoletoEntity b
            LEFT JOIN FETCH b.categoria
            WHERE b.user.id = ?1
              AND b.status = 'PENDENTE'
              AND b.vencimento < CURRENT_DATE
            """, userId).list();

        EntityManager em = getEntityManager();
        results.forEach(em::detach);

        return results;
    }

    public List<BoletoEntity> findPendingBoletosNearDueDate(LocalDate minDate, LocalDate maxDate) {
        List<BoletoEntity> results = find("""
            SELECT b FROM BoletoEntity b
            LEFT JOIN FETCH b.categoria
            WHERE b.status = 'PENDENTE'
              AND b.vencimento <= ?1
              AND b.vencimento >= ?2
            """, maxDate, minDate).list();

        EntityManager em = getEntityManager();
        results.forEach(em::detach);

        return results;
    }
}