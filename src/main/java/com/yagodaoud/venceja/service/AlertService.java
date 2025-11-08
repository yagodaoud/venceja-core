package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.repository.BoletoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Serviço para alertas de boletos próximos do vencimento
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final BoletoRepository boletoRepository;

    /**
     * Agenda verificação diária às 9h para boletos próximos do vencimento (3 dias)
     */
    @Scheduled(cron = "0 9 * * * ?")
    @Transactional(readOnly = true)
    public void checkPendingBoletosNearDueDate() {
        log.info("Iniciando verificação de boletos próximos do vencimento...");

        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(3);

        List<BoletoEntity> pendingBoletos = boletoRepository.findPendingBoletosNearDueDate(
                today,
                maxDate);

        log.info("Encontrados {} boletos próximos do vencimento", pendingBoletos.size());

        for (BoletoEntity boleto : pendingBoletos) {
            log.info("Alerta: Boleto ID {} - Fornecedor: {}, Vencimento: {}, Valor: R$ {}",
                    boleto.getId(),
                    boleto.getFornecedor(),
                    boleto.getVencimento(),
                    boleto.getValor());

            // TODO: Implementar webhook para Expo/React Native
            // Por enquanto, apenas log
        }

        log.info("Verificação de alertas concluída");
    }
}
