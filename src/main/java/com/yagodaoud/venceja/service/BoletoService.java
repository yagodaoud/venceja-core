```java
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço para gerenciamento de boletos
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

    /**
     * Cria um boleto manualmente (sem OCR)
     */
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
            categoria = categoriaRepository.findById(request.getCategoriaId())
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

        boleto = boletoRepository.save(boleto);
        log.info("Boleto criado manualmente: ID {}", boleto.getId());

        return toResponse(boleto);
    }

    /**
     * Atualiza um boleto
     */
    @Transactional
    public BoletoResponse updateBoleto(long id, BoletoRequest request, String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (request.getValor() == null || request.getVencimento() == null ||
                request.getFornecedor() == null || request.getFornecedor().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dados obrigatórios não fornecidos. Valor, vencimento e fornecedor são necessários.");
        }

        BoletoEntity boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        CategoriaEntity categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
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

        boleto = boletoRepository.save(boleto);
        log.info("Boleto atualizado: ID {}", boleto.getId());

        return toResponse(boleto);
    }

    /**
     * Processa upload de boleto com OCR assíncrono
     */
    @Asynchronous
    public CompletableFuture<BoletoResponse> scanBoleto(
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

            BoletoResponse response = createBoleto(mergedRequest, userEmail);

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            log.error("Erro ao processar boleto: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Lista boletos do usuário com paginação e filtros de período
     */
    @Transactional // readOnly not supported directly
    public Page<BoletoResponse> listBoletos(
            String userEmail,
            List<BoletoStatus> statuses,
            LocalDate dataInicio,
            LocalDate dataFim,
            Pageable pageable) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        updateOverdueBoletos(user.getId());

        // CHANGE: Pass list to repository
        Page<BoletoEntity> boletos = boletoRepository.findByUserIdWithFilters(
                user.getId(), statuses, dataInicio, dataFim, pageable);

        return boletos.map(this::toResponse);
    }

    /**
     * Marca boleto como pago
     */
    @Transactional
    public BoletoResponse pagarBoleto(
            Long boletoId,
            String userEmail,
            byte[] comprovanteBytes,
            String comprovanteName,
            Boolean semComprovante) throws IOException {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        BoletoEntity boleto = boletoRepository.findById(boletoId)
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

    /**
     * Deleta um boleto e seu comprovante associado do Firebase Storage, se existir
     * @param boletoId ID do boleto a ser deletado
     * @param userEmail Email do usuário autenticado
     */
    @Transactional
    public void deletarBoleto(Long boletoId, String userEmail) {
        // Busca o usuário
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Busca o boleto
        BoletoEntity boleto = boletoRepository.findById(boletoId)
                .orElseThrow(() -> new IllegalArgumentException("Boleto não encontrado"));

        // Verifica se o boleto pertence ao usuário
        if (!boleto.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Boleto não pertence ao usuário");
        }

        // Remove o arquivo do Firebase Storage se existir
        if (boleto.getComprovanteUrl() != null && !boleto.getComprovanteUrl().isEmpty()) {
            try {
                log.info("Removendo arquivo do Firebase Storage para o boleto ID: {}", boletoId);
                firebaseService.deleteFile(boleto.getComprovanteUrl());
            } catch (Exception e) {
                log.error("Erro ao remover arquivo do Firebase Storage para o boleto ID: {}", boletoId, e);
                // Não interrompe o fluxo, continua com a exclusão do boleto
            }
        }

        // Remove o boleto do banco de dados
        boletoRepository.delete(boleto);
        log.info("Boleto ID: {} removido com sucesso", boletoId);
    }
}
```