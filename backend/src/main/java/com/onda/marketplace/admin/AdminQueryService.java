package com.onda.marketplace.admin;

import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consultas administrativas de leitura e reprocessamento de outbox (M11).
 * Todos os métodos são somente-leitura, exceto {@link #reprocessarOutbox}.
 */
@Service
public class AdminQueryService {

    private final ServiceRequestRepository    srRepository;
    private final TransactionRepository       transactionRepository;
    private final OutboxEventRepository       outboxRepository;
    private final DisputeResolutionRepository resolutionRepository;

    public AdminQueryService(ServiceRequestRepository srRepository,
                             TransactionRepository transactionRepository,
                             OutboxEventRepository outboxRepository,
                             DisputeResolutionRepository resolutionRepository) {
        this.srRepository         = srRepository;
        this.transactionRepository = transactionRepository;
        this.outboxRepository     = outboxRepository;
        this.resolutionRepository = resolutionRepository;
    }

    @Transactional(readOnly = true)
    public List<DisputaAdminDto> findDisputas() {
        return srRepository.findByStatus(ServiceRequestStatus.EM_DISPUTA)
                .stream()
                .map(sr -> {
                    var valorRetido = transactionRepository.findByServiceRequestId(sr.getId())
                            .map(Transaction::getValorTotal)
                            .orElse(null);
                    return new DisputaAdminDto(sr.getId(), sr.getCategoria(),
                            valorRetido, sr.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DisputaDetalheDto findDetalheDisputa(UUID serviceRequestId) {
        ServiceRequest sr = srRepository.findById(serviceRequestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        var tx          = transactionRepository.findByServiceRequestId(serviceRequestId).orElse(null);
        var resolution  = resolutionRepository.findByServiceRequestId(serviceRequestId).orElse(null);

        return new DisputaDetalheDto(
                sr.getId(),
                sr.getCategoria(),
                sr.getStatus().name(),
                tx  != null ? tx.getValorTotal()        : null,
                tx  != null ? tx.getStatusPagamento()   : null,
                resolution != null ? resolution.getDecisao().name() : null,
                sr.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<TransacaoAdminDto> findTransacoes(TransactionStatus status) {
        return transactionRepository.findByStatusPagamento(status)
                .stream()
                .map(t -> new TransacaoAdminDto(
                        t.getId(), t.getServiceRequestId(), t.getValorTotal(), t.getStatusPagamento()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OutboxAdminDto> findOutbox(OutboxStatus status) {
        return outboxRepository.findByStatus(status)
                .stream()
                .map(e -> new OutboxAdminDto(
                        e.getId(), e.getAgregado(), e.getTipoEvento(), e.getTentativas(), e.getStatus()))
                .toList();
    }

    /**
     * Reenfileira um evento FALHA para PENDENTE.
     * Idempotente: se já está PENDENTE (e.g. chamada duplicada antes do processor rodar),
     * apenas salva novamente — sem efeito colateral.
     * PROCESSADO não é reprocessável: o gateway já executou a ação.
     */
    @Transactional
    public void reprocessarOutbox(UUID outboxId) {
        OutboxEvent event = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new BusinessException("OUTBOX_NOT_FOUND",
                        "Evento outbox não encontrado."));

        if (event.getStatus() == OutboxStatus.PROCESSADO) {
            throw new BusinessException("OUTBOX_NOT_REPROCESSABLE",
                    "Evento já processado não pode ser reenfileirado.");
        }

        event.resetarParaRetry();
        outboxRepository.save(event);
    }
}
