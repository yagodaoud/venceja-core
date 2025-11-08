package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.dto.BoletoRequest;
import com.yagodaoud.venceja.dto.BoletoResponse;
import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.entity.BoletoStatus;
import com.yagodaoud.venceja.entity.UserEntity;
import com.yagodaoud.venceja.repository.BoletoRepository;
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
    private final UserRepository userRepository;
    private final VisionService visionService;
    private final FirebaseService firebaseService;

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

            // Extrai dados do OCR ou usa dados fornecidos manualmente
            BigDecimal valor = (request != null && request.getValor() != null)
                    ? request.getValor()
                    : visionService.extractValor(ocrText);

            LocalDate vencimento = (request != null && request.getVencimento() != null)
                    ? request.getVencimento()
                    : visionService.extractVencimento(ocrText);

            String fornecedor = (request != null && request.getFornecedor() != null
                    && !request.getFornecedor().isEmpty())
                            ? request.getFornecedor()
                            : visionService.extractFornecedor(ocrText);

            String codigoBarras = (request != null && request.getCodigoBarras() != null
                    && !request.getCodigoBarras().isEmpty())
                            ? request.getCodigoBarras()
                            : visionService.extractCodigoBarras(ocrText);

            // Validações
            if (valor == null || vencimento == null || (fornecedor == null || fornecedor.isEmpty())) {
                throw new IllegalArgumentException(
                        "Dados obrigatórios não encontrados no OCR. Valor, vencimento e fornecedor são necessários.");
            }

            // Faz upload da imagem para Firebase
            String imageUrl = firebaseService.uploadBoletoImage(fileBytes, fileName);

            // Cria entidade
            BoletoEntity boleto = BoletoEntity.builder()
                    .user(user)
                    .fornecedor(fornecedor)
                    .valor(valor)
                    .vencimento(vencimento)
                    .codigoBarras(codigoBarras)
                    .status(determineStatus(vencimento))
                    .observacoes(request != null ? request.getObservacoes() : null)
                    .build();

            boleto = boletoRepository.save(boleto);

            log.info("Boleto criado com sucesso: ID {}", boleto.getId());

            return CompletableFuture.completedFuture(toResponse(boleto));

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
                .createdAt(boleto.getCreatedAt())
                .updatedAt(boleto.getUpdatedAt())
                .build();
    }
}
