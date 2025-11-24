package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.dto.BoletoRequest;
import com.yagodaoud.venceja.dto.BoletoResponse;
import com.yagodaoud.venceja.dto.CategoriaResponse;
import com.yagodaoud.venceja.dto.PagedResult;
import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.entity.BoletoStatus;
import com.yagodaoud.venceja.entity.CategoriaEntity;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.BoletoRepository;
import com.yagodaoud.venceja.repository.CategoriaRepository;
import com.yagodaoud.venceja.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Asynchronous;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciamento de boletos com otimizações de memória
 */
@Slf4j
@ApplicationScoped
public class BoletoService {

    @Inject
    BoletoRepository boletoRepository;

    @Inject
    CategoriaRepository categoriaRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    VisionService visionService;

    @Inject
    FirebaseService firebaseService;

    @Inject
    BoletoService self;

    @Inject
    EntityManager entityManager;

    @Transactional
    public BoletoResponse createBoleto(BoletoRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (request.getValor() == null || request.getVencimento() == null ||
                request.getFornecedor() == null || request.getFornecedor().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dados obrigatórios não fornecidos. Valor, vencimento e fornecedor são necessários.");
        }

        CategoriaEntity categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findByIdOptional(request.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

            if (!categoria.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Categoria não pertence ao usuário");
            }
        }

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

        boletoRepository.persist(boleto);

        entityManager.flush();

        BoletoResponse response = toResponse(boleto);

        entityManager.detach(boleto);

        log.info("Boleto criado manualmente: ID {}", boleto.getId());
        return response;
    }

    @Transactional
    public BoletoResponse updateBoleto(long id, BoletoRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (request.getValor() == null || request.getVencimento() == null ||
                request.getFornecedor() == null || request.getFornecedor().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dados obrigatórios não fornecidos. Valor, vencimento e fornecedor são necessários.");
        }

        BoletoEntity boleto = boletoRepository.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        CategoriaEntity categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findByIdOptional(request.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

            if (!categoria.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Categoria não pertence ao usuário");
            }
        }

        boleto.setFornecedor(request.getFornecedor());
        boleto.setValor(request.getValor());
        boleto.setVencimento(request.getVencimento());
        boleto.setCodigoBarras(request.getCodigoBarras());
        boleto.setObservacoes(request.getObservacoes());
        boleto.setCategoria(categoria);

        entityManager.flush();
        BoletoResponse response = toResponse(boleto);
        entityManager.detach(boleto);

        log.info("Boleto atualizado: ID {}", boleto.getId());
        return response;
    }

    @Asynchronous
    public CompletionStage<BoletoResponse> scanBoleto(
            byte[] fileBytes,
            String fileName,
            BoletoRequest request,
            String userEmail) {
        try {
            UserEntity user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            String ocrText = visionService.detectDocumentText(fileBytes);
            log.info("OCR concluído, texto extraído: {} caracteres", ocrText != null ? ocrText.length() : 0);

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

            BoletoResponse response = self.createBoleto(mergedRequest, userEmail);

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            log.error("Erro ao processar boleto: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Lista boletos com gestão de memória
     */
    @Transactional
    public PagedResult<BoletoResponse> listBoletos(
            String userEmail,
            List<BoletoStatus> statuses,
            LocalDate dataInicio,
            LocalDate dataFim,
            int page,
            int size,
            String sortBy,
            String direction) {

        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

//        updateOverdueBoletos(user.getId())

        long total = boletoRepository.countByUserIdWithFilters(
                user.getId(), statuses, dataInicio, dataFim);

        List<BoletoEntity> boletos = boletoRepository.findByUserIdWithFilters(
                user.getId(), statuses, dataInicio, dataFim, page, size, sortBy, direction);

        List<BoletoResponse> content = boletos.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        boletos.clear();

        entityManager.clear();

        return new PagedResult<>(content, total, page, size);
    }

    @Transactional
    public BoletoResponse pagarBoleto(
            Long boletoId,
            String userEmail,
            byte[] comprovanteBytes,
            String comprovanteName,
            Boolean semComprovante) throws IOException {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        BoletoEntity boleto = boletoRepository.findByIdOptional(boletoId)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        if (!boleto.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Boleto não pertence ao usuário");
        }

        boleto.setStatus(BoletoStatus.PAGO);

        if (comprovanteBytes != null && comprovanteBytes.length > 0) {
            String comprovanteUrl = firebaseService.uploadComprovante(comprovanteBytes, comprovanteName);
            boleto.setComprovanteUrl(comprovanteUrl);
            boleto.setSemComprovante(false);
        } else if (Boolean.TRUE.equals(semComprovante)) {
            boleto.setSemComprovante(true);
            boleto.setComprovanteUrl(null);
        }

        entityManager.flush();
        BoletoResponse response = toResponse(boleto);
        entityManager.detach(boleto);

        log.info("Boleto {} marcado como pago", boletoId);
        return response;
    }

    /**
     * Process in batches to avoid memory buildup
     */
    @Transactional
    public void updateOverdueBoletos(Long userId) {
        var overdueBoletos = boletoRepository.findOverdueBoletosByUserId(userId);

        int batchSize = 20;
        for (int i = 0; i < overdueBoletos.size(); i++) {
            BoletoEntity boleto = overdueBoletos.get(i);
            if (boleto.getStatus() == BoletoStatus.PENDENTE) {
                boleto.setStatus(BoletoStatus.VENCIDO);
            }

            if (i % batchSize == 0 && i > 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    private BoletoStatus determineStatus(LocalDate vencimento) {
        if (vencimento.isBefore(LocalDate.now())) {
            return BoletoStatus.VENCIDO;
        }
        return BoletoStatus.PENDENTE;
    }

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

    @Transactional
    public void deletarBoleto(Long boletoId, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        BoletoEntity boleto = boletoRepository.findByIdOptional(boletoId)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        if (!boleto.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Boleto não pertence ao usuário");
        }

        if (boleto.getComprovanteUrl() != null && !boleto.getComprovanteUrl().isEmpty()) {
            try {
                log.info("Removendo arquivo do Firebase Storage para o boleto ID: {}", boletoId);
                firebaseService.deleteFile(boleto.getComprovanteUrl());
            } catch (Exception e) {
                log.error("Erro ao remover arquivo do Firebase Storage para o boleto ID: {}", boletoId, e);
            }
        }

        boletoRepository.delete(boleto);
        entityManager.flush();
        entityManager.clear();

        log.info("Boleto ID: {} removido com sucesso", boletoId);
    }
}