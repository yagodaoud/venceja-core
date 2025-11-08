package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.dto.BoletoRequest;
import com.yagodaoud.venceja.dto.BoletoResponse;
import com.yagodaoud.venceja.dto.CategoriaRequest;
import com.yagodaoud.venceja.dto.CategoriaResponse;
import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.entity.BoletoStatus;
import com.yagodaoud.venceja.entity.CategoriaEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.BoletoRepository;
import com.yagodaoud.venceja.repository.CategoriaRepository;
import com.yagodaoud.venceja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

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
    public Page<CategoriaResponse> listCategorias(
            String userEmail,
            Pageable pageable) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        Page<CategoriaEntity> categorias = categoriaRepository.findByUserId(user.getId(), pageable);

        return categorias.map(this::toResponse);
    }

    /**
     * Converte entidade para DTO de resposta
     */
    private CategoriaResponse toResponse(CategoriaEntity categoria) {
        return CategoriaResponse.builder()
                .id(categoria.getId())
                .nome(categoria.getNome())
                .build();
    }
}
