package com.onda.marketplace.payment;

import com.onda.marketplace.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Component
@SuppressWarnings("null")
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private static final Set<String> PAYMENT_EVENTS =
            Set.of("PAYMENT_INITIATED", "PAYMENT_RELEASED", "PAYMENT_REFUNDED");

    private final TransactionRepository transactionRepository;
    private final GatewayService        gatewayService;
    private final NotificationService   notificationService;
    private final OutboxEventWriter     eventWriter;

    public OutboxProcessor(OutboxEventRepository outboxRepository,
                           TransactionRepository transactionRepository,
                           GatewayService gatewayService,
                           NotificationService notificationService,
                           OutboxEventWriter eventWriter) {
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processOutbox() {
        for (int i = 0; i < 20; i++) {
            OutboxEvent event = eventWriter.reservarProximo();
            if (event == null) return;
            try {
                if (PAYMENT_EVENTS.contains(event.getTipoEvento())) processarPagamento(event);
                else processarOutro(event);
            } catch (RuntimeException ex) {
                // Resultado externo pode já ter ocorrido. Não libera o claim para outro worker.
                log.error("Resultado outbox exige reconciliação: evento={}", event.getId());
            }
        }
    }

    private void processarPagamento(OutboxEvent event) {
        var tx = transactionRepository.findById(event.getAgregadoId()).orElse(null);
        if (tx == null) {
            eventWriter.salvarFalha(event);
            return;
        }
        try {
            switch (event.getTipoEvento()) {
                case "PAYMENT_INITIATED" -> {
                    if (tx.getStatusPagamento() == TransactionStatus.PENDENTE && tx.getGatewayTransactionId() == null)
                        despacharParaGateway(event.getTipoEvento(), tx);
                }
                case "PAYMENT_RELEASED" -> {
                    if (tx.getStatusPagamento() != TransactionStatus.LIBERADO) {
                        exigirRetido(tx);
                        despacharParaGateway(event.getTipoEvento(), tx);
                    }
                }
                case "PAYMENT_REFUNDED" -> {
                    if (tx.getStatusPagamento() != TransactionStatus.REEMBOLSADO) {
                        exigirRetido(tx);
                        despacharParaGateway(event.getTipoEvento(), tx);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao despachar outbox: evento={} tipo={}", event.getId(), event.getTipoEvento());
            eventWriter.salvarResultadoPagamento(tx, event, false);
            return;
        }
        // Persistência separada do catch do gateway: erro após envio mantém PROCESSANDO.
        eventWriter.salvarResultadoPagamento(tx, event, true);
    }

    private void exigirRetido(Transaction tx) {
        if (tx.getStatusPagamento() != TransactionStatus.RETIDO)
            throw new IllegalStateException("Operação exige pagamento retido");
    }

    private void despacharParaGateway(String tipo, Transaction tx) {
        switch (tipo) {
            case "PAYMENT_INITIATED" -> {
                String gwId = gatewayService.cobrar(tx);
                tx.setGatewayTransactionId(gwId);
            }
            case "PAYMENT_RELEASED"  -> gatewayService.liberar(tx);
            case "PAYMENT_REFUNDED"  -> gatewayService.reembolsar(tx);
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
        } catch (RuntimeException ex) {
            // Fica FALHA: aparece na reconciliação do painel (US27) e pode ser reprocessado.
            log.error("SOS não avisado ao admin: evento={}", event.getId());
            eventWriter.salvarFalha(event);
            return;
        }
        eventWriter.marcarProcessado(event);
    }
}
