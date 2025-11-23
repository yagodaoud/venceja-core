package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.dto.CategoriaRequest;
import com.yagodaoud.venceja.dto.CategoriaResponse;
import com.yagodaoud.venceja.dto.PagedResult;
import com.yagodaoud.venceja.entity.CategoriaEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.CategoriaRepository;
import com.yagodaoud.venceja.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciamento de categorias
 */
@Slf4j
@ApplicationScoped
public class CategoriaService {

    @Inject
    CategoriaRepository categoriaRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Lista categorias do usuário com paginação
     */
    @Transactional // readOnly not supported directly
    public PagedResult<CategoriaResponse> listCategorias(String userEmail, int page, int size) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        List<CategoriaEntity> categorias = categoriaRepository.findByUserId(user.getId(), page, size);
        long total = categoriaRepository.countByUserId(user.getId());

        List<CategoriaResponse> content = categorias.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PagedResult<>(content, total, page, size);
    }

    /**
     * Cria nova categoria
     */
    @Transactional
    public CategoriaResponse createCategoria(CategoriaRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        CategoriaEntity categoria = CategoriaEntity.builder()
                .user(user)
                .nome(request.getNome())
                .cor(request.getCor())
                .createdAt(LocalDateTime.now())
                .build();

        categoriaRepository.persist(categoria);
        log.info("Categoria criada: ID {}", categoria.getId());

        return toResponse(categoria);
    }

    /**
     * Atualiza categoria existente
     */
    @Transactional
    public CategoriaResponse updateCategoria(Long id, CategoriaRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        CategoriaEntity categoria = categoriaRepository.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        // Verifica se a categoria pertence ao usuário
        if (!categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Categoria não pertence ao usuário");
        }

        categoria.setNome(request.getNome());
        categoria.setCor(request.getCor());
        categoria.setUpdatedAt(LocalDateTime.now());

        // Entity is managed, changes are automatically flushed on commit
        log.info("Categoria atualizada: ID {}", categoria.getId());

        return toResponse(categoria);
    }

    /**
     * Deleta categoria
     */
    @Transactional
    public void deleteCategoria(Long id, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        CategoriaEntity categoria = categoriaRepository.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        if (!categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Categoria não pertence ao usuário");
        }

        categoriaRepository.delete(categoria);
        log.info("Categoria deletada: ID {}", id);
    }

    /**
     * Converte entidade para DTO de resposta
     */
    private CategoriaResponse toResponse(CategoriaEntity categoria) {
        return CategoriaResponse.builder()
                .id(categoria.getId())
                .nome(categoria.getNome())
                .cor(categoria.getCor())
                .createdAt(categoria.getCreatedAt())
                .build();
    }
}