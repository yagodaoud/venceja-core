package com.yagodaoud.venceja.dto;

import com.yagodaoud.venceja.entity.BoletoStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para resposta de boleto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoletoResponse {

    private Long id;
    private Long userId;
    private String fornecedor;
    private BigDecimal valor;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate vencimento;

    private String codigoBarras;
    private BoletoStatus status;
    private String comprovanteUrl;
    private Boolean semComprovante;
    private String observacoes;

    private CategoriaResponse categoria;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime updatedAt;
}
