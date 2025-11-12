package com.yagodaoud.venceja.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para informações de sessão ativa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveSessionResponse {

    private Long id;
    private String deviceInfo;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean current;
}