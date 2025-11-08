package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.dto.CategoriaRequest;
import com.yagodaoud.venceja.dto.CategoriaResponse;
import com.yagodaoud.venceja.entity.CategoriaEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.CategoriaRepository;
import com.yagodaoud.venceja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Serviço para gerenciamento de categorias
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UserRepository userRepository;

    /**
     * Lista categorias do usuário com paginação
     */
    @Transactional(readOnly = true)
    public Page<CategoriaResponse> listCategorias(String userEmail, Pageable pageable) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        Page<CategoriaEntity> categorias = categoriaRepository.findByUserId(user.getId(), pageable);
        return categorias.map(this::toResponse);
    }

    /**
     * Cria nova categoria
     */
    @Transactional
    public CategoriaResponse createCategoria(CategoriaRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        CategoriaEntity categoria = CategoriaEntity.builder()
                .user(user)
                .nome(request.getNome())
                .cor(request.getCor())
                .createdAt(LocalDateTime.now())
                .build();

        categoria = categoriaRepository.save(categoria);
        log.info("Categoria criada: ID {}", categoria.getId());

        return toResponse(categoria);
    }

    /**
     * Atualiza categoria existente
     */
    @Transactional
    public CategoriaResponse updateCategoria(Long id, CategoriaRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        CategoriaEntity categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        // Verifica se a categoria pertence ao usuário
        if (!categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Categoria não pertence ao usuário");
        }

        categoria.setNome(request.getNome());
        categoria.setCor(request.getCor());
        categoria.setUpdatedAt(LocalDateTime.now());

        categoria = categoriaRepository.save(categoria);
        log.info("Categoria atualizada: ID {}", categoria.getId());

        return toResponse(categoria);
    }

    /**
     * Deleta categoria
     */
    @Transactional
    public void deleteCategoria(Long id, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        CategoriaEntity categoria = categoriaRepository.findById(id)
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