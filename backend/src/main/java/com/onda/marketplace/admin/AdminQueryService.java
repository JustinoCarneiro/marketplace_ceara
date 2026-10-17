package com.onda.marketplace.admin;

import com.onda.marketplace.servicerequest.StorageService;
import com.onda.marketplace.audit.AuditService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.servicerequest.ServiceMediaRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.Cpf;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Consultas administrativas de leitura e reprocessamento de outbox (M11), e a fila de
 * repasse manual do Mercado Pago (MKT-49). Todos os métodos são somente-leitura, exceto
 * {@link #reprocessarOutbox} e {@link #confirmarRepasseManual}.
 */
@Service
@SuppressWarnings("null")
public class AdminQueryService {

    private final ServiceRequestRepository    srRepository;
    private final TransactionRepository       transactionRepository;
    private final OutboxEventRepository       outboxRepository;
    private final DisputeResolutionRepository resolutionRepository;
    private final ProposalRepository          proposalRepository;
    private final ServiceMediaRepository      mediaRepository;
    private final UserRepository              userRepository;
    private final ProviderProfileRepository   providerProfileRepository;
    private final CpfEncryptor                cpfEncryptor;
    private final AuditService                auditService;
    private final StorageService storageService;

    public AdminQueryService(ServiceRequestRepository srRepository,
                             TransactionRepository transactionRepository,
                             OutboxEventRepository outboxRepository,
                             DisputeResolutionRepository resolutionRepository,
                             ProposalRepository proposalRepository,
                             ServiceMediaRepository mediaRepository,
                             UserRepository userRepository,
                             ProviderProfileRepository providerProfileRepository,
                             CpfEncryptor cpfEncryptor,
                             AuditService auditService,
                             StorageService storageService) {
        this.srRepository         = srRepository;
        this.transactionRepository = transactionRepository;
        this.outboxRepository     = outboxRepository;
        this.resolutionRepository = resolutionRepository;
        this.proposalRepository   = proposalRepository;
        this.mediaRepository      = mediaRepository;
        this.userRepository       = userRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.cpfEncryptor         = cpfEncryptor;
        this.auditService         = auditService;
        this.storageService       = storageService;
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

        String prestadorNome = proposalRepository.findByServiceRequestIdAndStatus(serviceRequestId, ProposalStatus.ACEITA)
                .stream().findFirst()
                .flatMap(p -> userRepository.findById(p.getPrestadorId()))
                .map(User::getNome)
                .orElse(null);

        var midias = mediaRepository.findByServiceRequest_Id(serviceRequestId).stream()
                .map(m -> new DisputaDetalheDto.MidiaDto(m.getTipo().name(), storageService.readUrl(m.getUrl())))
                .toList();

        return new DisputaDetalheDto(
                sr.getId(),
                sr.getCategoria(),
                sr.getStatus().name(),
                tx  != null ? tx.getValorTotal()        : null,
                tx  != null ? tx.getStatusPagamento()   : null,
                sr.getMotivoDisputa(),
                sr.getDetalhesDisputa(),
                resolution != null ? resolution.getDecisao().name() : null,
                sr.getCreatedAt(),
                userRepository.findById(sr.getClienteId()).map(User::getNome).orElse("Cliente"),
                prestadorNome,
                midias);
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
                        e.getId(), e.getAgregado(), e.getAgregadoId(), e.getTipoEvento(),
                        e.getTentativas(), e.getStatus(), e.getProcessamentoEm()))
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
        outboxRepository.findTransactionIdByEventId(outboxId).ifPresent(txId -> {
            transactionRepository.findByIdComTrava(txId);
            if (outboxRepository.existsByAgregadoAndAgregadoIdAndStatus("transaction", txId, OutboxStatus.PROCESSANDO)) {
                throw new BusinessException("OUTBOX_IN_PROGRESS", "Há uma operação financeira em processamento para este pagamento.");
            }
        });
        OutboxEvent event = outboxRepository.findByIdComTrava(outboxId)
                .orElseThrow(() -> new BusinessException("OUTBOX_NOT_FOUND",
                        "Evento outbox não encontrado."));

        if (event.getStatus() == OutboxStatus.PROCESSANDO) {
            throw new BusinessException("OUTBOX_IN_PROGRESS", "Evento em processamento; confira a reconciliação antes de repetir.");
        }
        if (event.getStatus() == OutboxStatus.PROCESSADO) {
            throw new BusinessException("OUTBOX_NOT_REPROCESSABLE",
                    "Evento já processado não pode ser reenfileirado.");
        }

        event.resetarParaRetry();
        outboxRepository.save(event);
    }

    /**
     * Dados pro operador pagar manualmente o repasse (MKT-49, enquanto o Mercado Pago
     * não expõe payout Pix programático pra conta). A chave Pix decifrada só aparece
     * nesta consulta ROLE_ADMIN sob demanda — nunca em log, nunca em outro DTO.
     */
    @Transactional(readOnly = true)
    public RepassePendenteDto findRepassePendente(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSACTION_NOT_FOUND", "Transação não encontrada."));

        if (tx.getStatusPagamento() != TransactionStatus.RETIDO) {
            throw new BusinessException("TRANSACTION_NOT_RETAINED",
                    "Só transações RETIDO têm repasse pendente.");
        }

        UUID prestadorId = proposalRepository
                .findByServiceRequestIdAndStatus(tx.getServiceRequestId(), ProposalStatus.ACEITA)
                .stream().findFirst().map(Proposal::getPrestadorId)
                .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND",
                        "Proposta aceita não encontrada para este pedido."));

        String prestadorNome = userRepository.findById(prestadorId)
                .map(User::getNome).orElse("Prestador");

        ProviderProfile perfil = providerProfileRepository.findByUserId(prestadorId).orElse(null);
        boolean cadastrada = perfil != null && perfil.getChavePixCifrada() != null
                && !perfil.getChavePixCifrada().isBlank();
        String chavePix = cadastrada ? cpfEncryptor.decrypt(perfil.getChavePixCifrada()) : null;

        BigDecimal valorARepassar = tx.getValorTotal().subtract(tx.getValorComissao());

        return new RepassePendenteDto(tx.getId(), tx.getServiceRequestId(), prestadorId,
                prestadorNome, valorARepassar, cadastrada, chavePix);
    }

    /**
     * Confirma que o operador pagou o prestador fora do sistema e libera o escrow:
     * transição RETIDO→LIBERADO na Transaction (guardada/idempotente na entidade) e o(s)
     * evento(s) PAYMENT_RELEASED em FALHA da mesma transação viram PROCESSADO — sem isso
     * o "reprocessar" padrão chamaria {@code GatewayService.liberar} de novo e falharia
     * de novo (payout ainda não é automático).
     */
    @Transactional
    public void confirmarRepasseManual(UUID transactionId, UUID adminId, String comprovanteReferencia) {
        if (comprovanteReferencia == null || comprovanteReferencia.isBlank()) {
            throw new BusinessException("PAYOUT_RECEIPT_REQUIRED", "Informe a referência do comprovante do repasse.");
        }
        // A referência vai para a auditoria, que é retida e lida por todo admin: não pode ser dado pessoal (um CPF colado por engano) nem
        // uma chave Pix aleatória. O padrão da API (letras, dígitos e . _ : / -) não barra os dois; aqui sim (revisão cruzada, 2026-10-14).
        if (pareceDadoPessoal(comprovanteReferencia)) {
            throw new BusinessException("PAYOUT_RECEIPT_INVALID",
                    "A referência deve ser o identificador do comprovante, não um CPF nem uma chave Pix.");
        }
        Transaction tx = transactionRepository.findByIdComTrava(transactionId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSACTION_NOT_FOUND", "Transação não encontrada."));

        if (tx.getStatusPagamento() == TransactionStatus.LIBERADO) {
            return; // repetição da confirmação: não duplica auditoria
        }

        // Repasse só de pedido CONCLUIDO: a API liberava qualquer transação RETIDO, e um pedido em disputa ou em andamento teria o escrow dado
        // como pago (a mediação depois não conseguiria reembolsar). A UI só mostra a fila certa; a regra vale também na API.
        ServiceRequest pedido = srRepository.findById(tx.getServiceRequestId())
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido da transação não encontrado."));
        if (pedido.getStatus() != ServiceRequestStatus.CONCLUIDO) {
            throw new BusinessException("PAYOUT_NOT_DUE", "O repasse só pode ser confirmado em pedido concluído.");
        }

        if (tx.getStatusPagamento() == TransactionStatus.RETIDO && outboxRepository.existsByAgregadoAndAgregadoIdAndStatus("transaction", transactionId, OutboxStatus.PROCESSANDO)) {
            throw new BusinessException("OUTBOX_IN_PROGRESS", "Há uma operação financeira em processamento para este pagamento.");
        }
        tx.liberar();
        transactionRepository.save(tx);

        outboxRepository.findByAgregadoIdAndTipoEvento(transactionId, "PAYMENT_RELEASED")
                .stream()
                .filter(e -> e.getStatus() != OutboxStatus.PROCESSADO)
                .forEach(e -> {
                    e.marcarProcessado();
                    outboxRepository.save(e);
                });
        auditService.registrar(adminId, "CONFIRMAR_REPASSE_MANUAL", "transaction", transactionId,
                "comprovante=" + comprovanteReferencia);
    }

    private static final Pattern FORMATO_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** CPF (com ou sem máscara, validado pelos dígitos verificadores) ou formato de chave Pix aleatória (UUID). */
    private static boolean pareceDadoPessoal(String referencia) {
        String r = referencia.trim();
        return Cpf.valido(r) || FORMATO_UUID.matcher(r).matches();
    }
}
