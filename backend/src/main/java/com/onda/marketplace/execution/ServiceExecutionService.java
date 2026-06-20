package com.onda.marketplace.execution;

import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ServiceExecutionService {

    private static final Set<ServiceRequestStatus> CANCELAVEIS =
            Set.of(ServiceRequestStatus.ACEITO, ServiceRequestStatus.EM_ANDAMENTO);

    private final ServiceRequestRepository srRepository;
    private final ProposalRepository       proposalRepository;
    private final TransactionRepository    transactionRepository;
    private final OutboxEventRepository    outboxRepository;
    private final NotificationService      notificationService;

    public ServiceExecutionService(ServiceRequestRepository srRepository,
                                   ProposalRepository proposalRepository,
                                   TransactionRepository transactionRepository,
                                   OutboxEventRepository outboxRepository,
                                   NotificationService notificationService) {
        this.srRepository          = srRepository;
        this.proposalRepository    = proposalRepository;
        this.transactionRepository = transactionRepository;
        this.outboxRepository      = outboxRepository;
        this.notificationService   = notificationService;
    }

    /** ACEITO → EM_ANDAMENTO. Verifica que o prestador autenticado é o dono da proposta aceita. */
    @Transactional
    public void start(UUID srId, UUID prestadorId) {
        ServiceRequest sr = srRepository.findById(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (sr.getStatus() != ServiceRequestStatus.ACEITO) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "start() exige status ACEITO, atual: " + sr.getStatus());
        }

        proposalRepository.findByServiceRequestIdAndStatus(srId, ProposalStatus.ACEITA)
                .stream().findFirst()
                .filter(p -> p.getPrestadorId().equals(prestadorId))
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED_PROVIDER",
                        "Prestador não é o responsável por este pedido."));

        sr.setStatus(ServiceRequestStatus.EM_ANDAMENTO);
        srRepository.save(sr);
    }

    /** EM_ANDAMENTO → CONCLUIDO. Escrita atômica: status + OutboxEvent(PAYMENT_RELEASED). */
    @Transactional
    public void confirmCompletion(UUID srId, UUID clienteId) {
        ServiceRequest sr = srRepository.findByIdAndCliente_Id(srId, clienteId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (sr.getStatus() != ServiceRequestStatus.EM_ANDAMENTO) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "confirmCompletion() exige status EM_ANDAMENTO, atual: " + sr.getStatus());
        }

        sr.setStatus(ServiceRequestStatus.CONCLUIDO);
        srRepository.save(sr);

        transactionRepository.findByServiceRequestId(srId).ifPresent(tx ->
                outboxRepository.save(outboxEvent(tx, "PAYMENT_RELEASED")));
    }

    /** EM_ANDAMENTO → EM_DISPUTA. Qualquer parte autenticada pode abrir disputa. */
    @Transactional
    public void openDispute(UUID srId) {
        ServiceRequest sr = srRepository.findById(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (sr.getStatus() != ServiceRequestStatus.EM_ANDAMENTO) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "openDispute() exige status EM_ANDAMENTO, atual: " + sr.getStatus());
        }

        sr.setStatus(ServiceRequestStatus.EM_DISPUTA);
        srRepository.save(sr);

        // M12: alerta ao admin — disputa aberta exige mediação
        notificationService.criarAlerta("DISPUTA", srId);
    }

    /** ACEITO | EM_ANDAMENTO → CANCELADO. Se transação RETIDA, gera OutboxEvent(PAYMENT_REFUNDED). */
    @Transactional
    public void cancel(UUID srId) {
        ServiceRequest sr = srRepository.findById(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (!CANCELAVEIS.contains(sr.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "cancel() exige ACEITO ou EM_ANDAMENTO, atual: " + sr.getStatus());
        }

        sr.setStatus(ServiceRequestStatus.CANCELADO);
        srRepository.save(sr);

        transactionRepository.findByServiceRequestId(srId)
                .filter(tx -> tx.getStatusPagamento() == TransactionStatus.RETIDO)
                .ifPresent(tx -> outboxRepository.save(outboxEvent(tx, "PAYMENT_REFUNDED")));
    }

    private OutboxEvent outboxEvent(Transaction tx, String tipo) {
        String payload = String.format(
                "{\"transactionId\":\"%s\",\"serviceRequestId\":\"%s\"}", tx.getId(), tx.getServiceRequestId());
        return new OutboxEvent("transaction", tx.getId(), tipo, payload);
    }
}
