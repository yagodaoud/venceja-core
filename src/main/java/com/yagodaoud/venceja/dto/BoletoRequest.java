package com.yagodaoud.venceja.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para criação/edição de boleto
 */
@Data
public class BoletoRequest {

    @NotBlank(message = "Fornecedor é obrigatório")
    private String fornecedor;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser positivo")
    private BigDecimal valor;

    @NotNull(message = "Vencimento é obrigatório")
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate vencimento;

    private String codigoBarras;

    private String observacoes;
}
