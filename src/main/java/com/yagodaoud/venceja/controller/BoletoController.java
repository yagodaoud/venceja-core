package com.yagodaoud.venceja.controller;

import com.yagodaoud.venceja.dto.ApiResponse;
import com.yagodaoud.venceja.dto.BoletoRequest;
import com.yagodaoud.venceja.dto.BoletoResponse;
import com.yagodaoud.venceja.entity.BoletoStatus;
import com.yagodaoud.venceja.service.BoletoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller para gerenciamento de boletos
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/boletos")
@RequiredArgsConstructor
public class BoletoController {

    private final BoletoService boletoService;
    private final ObjectMapper objectMapper;

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<BoletoResponse>> scanBoleto(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "data", required = false) String dataJson,
            Authentication authentication) {
        try {
            String userEmail = ((UserDetails) authentication.getPrincipal()).getUsername();

            BoletoRequest request = null;
            if (dataJson != null && !dataJson.isEmpty()) {
                try {
                    request = objectMapper.readValue(dataJson, BoletoRequest.class);
                } catch (Exception e) {
                    log.warn("Erro ao parsear JSON de edição: {}", e.getMessage());
                }
            }

            if (request == null) {
                request = new BoletoRequest();
            }

            CompletableFuture<BoletoResponse> future = boletoService.scanBoleto(file, request, userEmail);
            BoletoResponse response = future.get(); // Aguarda conclusão

            ApiResponse<BoletoResponse> apiResponse = ApiResponse.<BoletoResponse>builder()
                    .data(response)
                    .message("Boleto processado com sucesso")
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);

        } catch (Exception e) {
            log.error("Erro ao processar boleto: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar boleto: " + e.getMessage(), e);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<BoletoResponse>>> listBoletos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        String userEmail = ((UserDetails) authentication.getPrincipal()).getUsername();

        Pageable pageable = PageRequest.of(page, size);
        BoletoStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = BoletoStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Status inválido: " + status + ". Valores aceitos: PENDENTE, PAGO, VENCIDO");
            }
        }

        Page<BoletoResponse> boletos = boletoService.listBoletos(userEmail, statusEnum, pageable);

        ApiResponse.Meta meta = ApiResponse.Meta.builder()
                .total(boletos.getTotalElements())
                .page(page)
                .size(size)
                .build();

        ApiResponse<Page<BoletoResponse>> response = ApiResponse.<Page<BoletoResponse>>builder()
                .data(boletos)
                .message("Boletos listados com sucesso")
                .meta(meta)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300))
                .eTag(String.valueOf(boletos.getTotalElements()))
                .body(response);
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<ApiResponse<BoletoResponse>> pagarBoleto(
            @PathVariable Long id,
            @RequestPart(value = "comprovante", required = false) MultipartFile comprovante,
            @RequestParam(value = "semComprovante", required = false, defaultValue = "false") Boolean semComprovante,
            Authentication authentication) {
        try {
            String userEmail = ((UserDetails) authentication.getPrincipal()).getUsername();

            BoletoResponse response = boletoService.pagarBoleto(id, userEmail, comprovante, semComprovante);

            ApiResponse<BoletoResponse> apiResponse = ApiResponse.<BoletoResponse>builder()
                    .data(response)
                    .message("Boleto marcado como pago")
                    .build();

            return ResponseEntity.ok(apiResponse);

        } catch (Exception e) {
            log.error("Erro ao marcar boleto como pago: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao marcar boleto como pago: " + e.getMessage(), e);
        }
    }
}
