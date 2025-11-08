package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.dto.BoletoRequest;
import com.yagodaoud.venceja.dto.BoletoResponse;
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
 * Serviço para gerenciamento de boletos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoletoService {

    private final BoletoRepository boletoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UserRepository userRepository;
    private final VisionService visionService;
    private final FirebaseService firebaseService;

    /**
     * Cria um boleto manualmente (sem OCR)
     */
    @Transactional
    public BoletoResponse createBoleto(BoletoRequest request, String userEmail) {
        // Valida usuário
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // Valida dados obrigatórios
        if (request.getValor() == null || request.getVencimento() == null ||
                request.getFornecedor() == null || request.getFornecedor().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dados obrigatórios não fornecidos. Valor, vencimento e fornecedor são necessários.");
        }

        // Valida e busca categoria se fornecida
        CategoriaEntity categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

            if (!categoria.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Categoria não pertence ao usuário");
            }
        }

        // Cria entidade
        BoletoEntity boleto = BoletoEntity.builder()
                .user(user)
                .fornecedor(request.getFornecedor())
                .valor(request.getValor())
                .vencimento(request.getVencimento())
                .codigoBarras(request.getCodigoBarras())
                .status(determineStatus(request.getVencimento()))
                .observacoes(request.getObservacoes())
                .categoria(categoria)
                .build();

        boleto = boletoRepository.save(boleto);
        log.info("Boleto criado manualmente: ID {}", boleto.getId());

        return toResponse(boleto);
    }

    /**
     * Atualiza um boleto
     */
    @Transactional
    public BoletoResponse updateBoleto(long id, BoletoRequest request, String userEmail) {
        // Valida usuário
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // Valida dados obrigatórios
        if (request.getValor() == null || request.getVencimento() == null ||
                request.getFornecedor() == null || request.getFornecedor().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dados obrigatórios não fornecidos. Valor, vencimento e fornecedor são necessários.");
        }

        // Valida e busca boleto
        BoletoEntity boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        // Valida e busca categoria se fornecida
        CategoriaEntity categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

            if (!categoria.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Categoria não pertence ao usuário");
            }
        }

        // Atualiza entidade
        boleto.setFornecedor(request.getFornecedor());
        boleto.setValor(request.getValor());
        boleto.setVencimento(request.getVencimento());
        boleto.setCodigoBarras(request.getCodigoBarras());
        boleto.setObservacoes(request.getObservacoes());
        boleto.setCategoria(categoria);

        boleto = boletoRepository.save(boleto);
        log.info("Boleto atualizado: ID {}", boleto.getId());

        return toResponse(boleto);
    }


    /**
     * Processa upload de boleto com OCR assíncrono
     */
    @Async("taskExecutor")
    public CompletableFuture<BoletoResponse> scanBoleto(
            MultipartFile file,
            BoletoRequest request,
            String userEmail) {
        try {
            UserEntity user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

            byte[] fileBytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            // Faz OCR do documento
            String ocrText = visionService.detectDocumentText(fileBytes);
            log.info("OCR concluído, texto extraído: {} caracteres", ocrText != null ? ocrText.length() : 0);

            // Monta request com dados do OCR (se não fornecidos manualmente)
            BoletoRequest mergedRequest = new BoletoRequest();

            mergedRequest.setValor(
                    (request != null && request.getValor() != null)
                            ? request.getValor()
                            : visionService.extractValor(ocrText)
            );

            mergedRequest.setVencimento(
                    (request != null && request.getVencimento() != null)
                            ? request.getVencimento()
                            : visionService.extractVencimento(ocrText)
            );

            mergedRequest.setFornecedor(
                    (request != null && request.getFornecedor() != null && !request.getFornecedor().isEmpty())
                            ? request.getFornecedor()
                            : visionService.extractFornecedor(ocrText)
            );

            mergedRequest.setCodigoBarras(
                    (request != null && request.getCodigoBarras() != null && !request.getCodigoBarras().isEmpty())
                            ? request.getCodigoBarras()
                            : visionService.extractCodigoBarras(ocrText)
            );

            mergedRequest.setObservacoes(request != null ? request.getObservacoes() : null);
            mergedRequest.setCategoriaId(request != null ? request.getCategoriaId() : null);

            // Faz upload da imagem para Firebase
            String imageUrl = firebaseService.uploadBoletoImage(fileBytes, fileName);
            log.info("Imagem do boleto salva: {}", imageUrl);

            // Cria o boleto usando o método centralizado
            BoletoResponse response = createBoleto(mergedRequest, userEmail);

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            log.error("Erro ao processar boleto: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Lista boletos do usuário com paginação
     */
    @Transactional(readOnly = true)
    public Page<BoletoResponse> listBoletos(
            String userEmail,
            BoletoStatus status,
            Pageable pageable) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // Atualiza status de boletos vencidos
        updateOverdueBoletos(user.getId());

        Page<BoletoEntity> boletos;
        if (status != null) {
            boletos = boletoRepository.findByUserIdAndStatus(user.getId(), status, pageable);
        } else {
            boletos = boletoRepository.findByUserIdAndOptionalStatus(user.getId(), null, pageable);
        }

        return boletos.map(this::toResponse);
    }

    /**
     * Marca boleto como pago
     */
    @Transactional
    public BoletoResponse pagarBoleto(
            Long boletoId,
            String userEmail,
            MultipartFile comprovante,
            Boolean semComprovante) throws IOException {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        BoletoEntity boleto = boletoRepository.findById(boletoId)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        if (!boleto.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Boleto não pertence ao usuário");
        }

        boleto.setStatus(BoletoStatus.PAGO);

        if (comprovante != null && !comprovante.isEmpty()) {
            byte[] fileBytes = comprovante.getBytes();
            String fileName = comprovante.getOriginalFilename();
            String comprovanteUrl = firebaseService.uploadComprovante(fileBytes, fileName);
            boleto.setComprovanteUrl(comprovanteUrl);
            boleto.setSemComprovante(false);
        } else if (Boolean.TRUE.equals(semComprovante)) {
            boleto.setSemComprovante(true);
            boleto.setComprovanteUrl(null);
        }

        boleto = boletoRepository.save(boleto);

        log.info("Boleto {} marcado como pago", boletoId);

        return toResponse(boleto);
    }

    /**
     * Atualiza status de boletos vencidos
     */
    @Transactional
    public void updateOverdueBoletos(Long userId) {
        var overdueBoletos = boletoRepository.findOverdueBoletosByUserId(userId);
        overdueBoletos.forEach(boleto -> {
            if (boleto.getStatus() == BoletoStatus.PENDENTE) {
                boleto.setStatus(BoletoStatus.VENCIDO);
                boletoRepository.save(boleto);
            }
        });
    }

    /**
     * Determina status inicial do boleto baseado na data de vencimento
     */
    private BoletoStatus determineStatus(LocalDate vencimento) {
        if (vencimento.isBefore(LocalDate.now())) {
            return BoletoStatus.VENCIDO;
        }
        return BoletoStatus.PENDENTE;
    }

    /**
     * Converte entidade para DTO de resposta
     */
    private BoletoResponse toResponse(BoletoEntity boleto) {
        CategoriaResponse categoriaResponse = null;
        if (boleto.getCategoria() != null) {
            categoriaResponse = CategoriaResponse.builder()
                    .id(boleto.getCategoria().getId())
                    .nome(boleto.getCategoria().getNome())
                    .cor(boleto.getCategoria().getCor())
                    .build();
        }

        return BoletoResponse.builder()
                .id(boleto.getId())
                .userId(boleto.getUser().getId())
                .fornecedor(boleto.getFornecedor())
                .valor(boleto.getValor())
                .vencimento(boleto.getVencimento())
                .codigoBarras(boleto.getCodigoBarras())
                .status(boleto.getStatus())
                .comprovanteUrl(boleto.getComprovanteUrl())
                .semComprovante(boleto.getSemComprovante())
                .observacoes(boleto.getObservacoes())
                .categoria(categoriaResponse)
                .createdAt(boleto.getCreatedAt())
                .updatedAt(boleto.getUpdatedAt())
                .build();
    }
}