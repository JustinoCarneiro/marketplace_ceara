package com.onda.marketplace.payment;

import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transações curtas de reserva/conclusão; nenhum acesso ao gateway neste bean. */
@Component
public class OutboxEventWriter {
    private final OutboxEventRepository outboxRepository;
    private final TransactionRepository transactionRepository;

    public OutboxEventWriter(OutboxEventRepository outboxRepository, TransactionRepository transactionRepository) {
        this.outboxRepository = outboxRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxEvent reservarProximo() {
        for (var event : outboxRepository.candidatoNaoFinanceiroComTrava()) {
            event.reservar();
            return outboxRepository.saveAndFlush(event);
        }
        for (var id : outboxRepository.candidatoFinanceiroComTrava()) {
            // Nova consulta após adquirir a trava da Transaction: reavalia snapshot SQL concorrente.
            var event = outboxRepository.findByIdComTrava(id).orElseThrow();
            if (event.getStatus() == OutboxStatus.PENDENTE && !outboxRepository
                    .existsByAgregadoAndAgregadoIdAndStatus(event.getAgregado(), event.getAgregadoId(), OutboxStatus.PROCESSANDO)) {
                event.reservar();
                return outboxRepository.saveAndFlush(event);
            }
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarResultadoPagamento(Transaction snapshot, OutboxEvent claim, boolean sucesso) {
        // Ordem única também no admin: Transaction -> OutboxEvent.
        var current = transactionRepository.findByIdComTrava(claim.getAgregadoId()).orElseThrow();
        var event = outboxRepository.findByIdComTrava(claim.getId()).orElseThrow();
        if (!event.pertenceAoClaim(claim)) return;
        if (sucesso) {
            switch (event.getTipoEvento()) {
                case "PAYMENT_INITIATED" -> {
                    if (current.getGatewayTransactionId() != null && !current.getGatewayTransactionId().equals(snapshot.getGatewayTransactionId())) {
                        throw new BusinessException("GATEWAY_RESULT_CONFLICT", "Resultado financeiro exige reconciliação.");
                    }
                    current.setGatewayTransactionId(snapshot.getGatewayTransactionId());
                    current.setPixQrCode(snapshot.getPixQrCode());
                    current.setPixQrCodeBase64(snapshot.getPixQrCodeBase64());
                    current.setPixTicketUrl(snapshot.getPixTicketUrl());
                    current.setBandeira(snapshot.getBandeira());
                    current.setCardLast4(snapshot.getCardLast4());
                }
                case "PAYMENT_RELEASED" -> current.liberar();
                case "PAYMENT_REFUNDED" -> current.reembolsar();
                default -> throw new IllegalStateException("Evento financeiro inválido");
            }
            // Dirty checking somente da entidade fresca; nunca merge do snapshot do gateway.
            event.marcarProcessado();
        } else event.marcarFalha();
        outboxRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarFalha(OutboxEvent claim) { concluir(claim, false); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarProcessado(OutboxEvent claim) { concluir(claim, true); }

    private void concluir(OutboxEvent claim, boolean sucesso) {
        // Usado para SOS, eventos desconhecidos e referência financeira inexistente.
        var event = outboxRepository.findByIdComTrava(claim.getId()).orElseThrow();
        if (!event.pertenceAoClaim(claim)) return;
        if (sucesso) event.marcarProcessado(); else event.marcarFalha();
        outboxRepository.save(event);
    }
}
