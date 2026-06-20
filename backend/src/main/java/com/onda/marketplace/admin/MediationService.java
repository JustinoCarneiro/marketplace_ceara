package com.onda.marketplace.admin;

import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Mediação de disputas (US24). Resolve um pedido EM_DISPUTA movendo-o para
 * CONCLUIDO (libera escrow) ou CANCELADO (reembolsa).
 *
 * O dinheiro NÃO é movido aqui: a transição de estado financeiro é dirigida por
 * evento confirmado. Gravamos o novo estado do pedido + um OutboxEvent na mesma
 * transação de banco; o {@code OutboxProcessor} chama o gateway fora de
 * {@code @Transactional} (princípio Escrow/Saga do CLAUDE.md).
 */
@Service
@SuppressWarnings("null")
public class MediationService {

    private final ServiceRequestRepository    srRepository;
    private final TransactionRepository       transactionRepository;
    private final OutboxEventRepository       outboxRepository;
    private final DisputeResolutionRepository resolutionRepository;

    public MediationService(ServiceRequestRepository srRepository,
                            TransactionRepository transactionRepository,
                            OutboxEventRepository outboxRepository,
                            DisputeResolutionRepository resolutionRepository) {
        this.srRepository          = srRepository;
        this.transactionRepository = transactionRepository;
        this.outboxRepository      = outboxRepository;
        this.resolutionRepository  = resolutionRepository;
    }

    @Transactional
    public void resolver(UUID serviceRequestId, UUID adminId, ResolveDisputeRequest req) {
        ServiceRequest sr = srRepository.findById(serviceRequestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (sr.getStatus() != ServiceRequestStatus.EM_DISPUTA) {
            throw new BusinessException("NOT_IN_DISPUTE",
                    "Apenas pedidos EM_DISPUTA podem ser mediados.");
        }

        Transaction tx = transactionRepository.findByServiceRequestId(serviceRequestId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND",
                        "Transação do pedido não encontrada."));

        String tipoEvento;
        if (req.decisao() == MediationDecision.CONCLUIR) {
            sr.setStatus(ServiceRequestStatus.CONCLUIDO);
            tipoEvento = "PAYMENT_RELEASED";
        } else {
            sr.setStatus(ServiceRequestStatus.CANCELADO);
            tipoEvento = "PAYMENT_REFUNDED";
        }
        srRepository.save(sr);

        // agregadoId = id da Transaction → o OutboxProcessor resolve a tx por findById
        String payload = String.format(
                "{\"transactionId\":\"%s\",\"decisao\":\"%s\"}", tx.getId(), req.decisao());
        outboxRepository.save(new OutboxEvent("transaction", tx.getId(), tipoEvento, payload));

        // trilha de auditoria imutável da decisão administrativa
        resolutionRepository.save(
                new DisputeResolution(serviceRequestId, adminId, req.decisao(), req.justificativa()));
    }
}
