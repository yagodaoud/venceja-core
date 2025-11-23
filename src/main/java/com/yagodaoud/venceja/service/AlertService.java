package com.yagodaoud.venceja.service;

import com.yagodaoud.venceja.entity.BoletoEntity;
import com.yagodaoud.venceja.repository.BoletoRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

/**
 * Serviço para alertas de boletos próximos do vencimento
 */
@Slf4j
@ApplicationScoped
public class AlertService {

    @Inject
    BoletoRepository boletoRepository;

    /**
     * Agenda verificação diária às 9h para boletos próximos do vencimento (3 dias)
     * Cron: 0 0 9 * * ? (At 09:00:00am every day)
     * Note: The original cron was "0 9 * * * ?", which means minute 9 of every hour.
     * I am correcting it to "0 0 9 * * ?" based on the comment "diária às 9h".
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
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
