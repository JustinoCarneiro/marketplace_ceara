package com.onda.marketplace.payment;

import com.onda.marketplace.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@SuppressWarnings("null")
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private static final Set<String> PAYMENT_EVENTS =
            Set.of("PAYMENT_INITIATED", "PAYMENT_RELEASED", "PAYMENT_REFUNDED");

    private final OutboxEventRepository outboxRepository;
    private final TransactionRepository transactionRepository;
    private final GatewayService        gatewayService;
    private final NotificationService   notificationService;
    private final OutboxEventWriter     eventWriter;

    public OutboxProcessor(OutboxEventRepository outboxRepository,
                           TransactionRepository transactionRepository,
                           GatewayService gatewayService,
                           NotificationService notificationService,
                           OutboxEventWriter eventWriter) {
        this.outboxRepository      = outboxRepository;
        this.transactionRepository = transactionRepository;
        this.gatewayService        = gatewayService;
        this.notificationService   = notificationService;
        this.eventWriter           = eventWriter;
    }

    /**
     * Processa eventos pendentes. Sem @Transactional — gateway NUNCA dentro de transação
     * de banco (princípio Escrow/Saga do CLAUDE.md). Cada evento é persistido em transação
     * própria, separada da chamada ao gateway.
     */
    @Scheduled(fixedDelayString = "${marketplace.outbox.delay-ms:5000}")
    public void processOutbox() {
        List<OutboxEvent> pendentes = outboxRepository
                .findTop20ByStatusOrderByCriadoEmAsc(OutboxStatus.PENDENTE);

        for (OutboxEvent event : pendentes) {
            if (PAYMENT_EVENTS.contains(event.getTipoEvento())) {
                processarPagamento(event);
            } else {
                processarOutro(event);
            }
        }
    }

    private void processarPagamento(OutboxEvent event) {
        transactionRepository.findById(event.getAgregadoId()).ifPresent(tx -> {
            try {
                despacharParaGateway(event.getTipoEvento(), tx);
                eventWriter.salvarResultadoPagamento(tx, event, true);
            } catch (Exception ex) {
                log.warn("Falha ao processar {} para tx={}: {}", event.getTipoEvento(),
                        tx.getId(), ex.getMessage());
                eventWriter.salvarResultadoPagamento(tx, event, false);
            }
        });
    }

    private void despacharParaGateway(String tipo, Transaction tx) {
        switch (tipo) {
            case "PAYMENT_INITIATED" -> {
                String gwId = gatewayService.cobrar(tx);
                tx.setGatewayTransactionId(gwId);
            }
            case "PAYMENT_RELEASED"  -> { gatewayService.liberar(tx);    tx.liberar(); }
            case "PAYMENT_REFUNDED"  -> { gatewayService.reembolsar(tx); tx.reembolsar(); }
            default -> log.warn("Tipo de evento de pagamento desconhecido: {}", tipo);
        }
    }

    // Eventos não-financeiros. SOS_TRIGGERED entrega o alerta de verdade — antes daqui só
    // saía um log.warn e o evento era marcado como processado, ou seja, o caminho durável
    // do SOS existia e não fazia nada (US30: o aviso não pode depender só do painel).
    private void processarOutro(OutboxEvent event) {
        if ("SOS_TRIGGERED".equals(event.getTipoEvento())) {
            entregarSos(event);
            return;
        }
        log.info("Evento outbox não reconhecido: tipo={}", event.getTipoEvento());
        eventWriter.marcarProcessado(event);
    }

    private void entregarSos(OutboxEvent event) {
        try {
            notificationService.entregar("SOS", event.getAgregadoId());
            eventWriter.marcarProcessado(event);
        } catch (RuntimeException ex) {
            // Fica FALHA: aparece na reconciliação do painel (US27) e pode ser reprocessado.
            log.error("SOS acionado e NÃO avisado ao admin (evento={}, ref={}): {}",
                    event.getId(), event.getAgregadoId(), ex.getMessage(), ex);
            eventWriter.salvarFalha(event);
        }
    }
}
