package com.onda.marketplace.payment;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class PaymentService {

    private final TransactionRepository    transactionRepository;
    private final OutboxEventRepository    outboxRepository;
    private final ServiceRequestRepository requestRepository;
    private final ProposalRepository       proposalRepository;
    private final UserRepository           userRepository;
    private final BigDecimal               percentualComissao;

    public PaymentService(
            TransactionRepository transactionRepository,
            OutboxEventRepository outboxRepository,
            ServiceRequestRepository requestRepository,
            ProposalRepository proposalRepository,
            UserRepository userRepository,
            @Value("${marketplace.comissao:0.10}") BigDecimal percentualComissao) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository      = outboxRepository;
        this.requestRepository     = requestRepository;
        this.proposalRepository    = proposalRepository;
        this.userRepository        = userRepository;
        this.percentualComissao    = percentualComissao;
    }

    /** Percentual de comissão vigente (0.10 = 10%) — o mesmo que a cobrança aplica. */
    public BigDecimal percentualComissao() {
        return percentualComissao;
    }

    /**
     * Inicia o pagamento: escreve Transaction + OutboxEvent em UMA transação de banco.
     * O gateway NÃO é chamado aqui — é chamado pelo OutboxProcessor (sem @Transactional).
     * Princípio Escrow/Saga (CLAUDE.md): gateway nunca dentro de @Transactional.
     */
    @Transactional
    public TransactionDto initiate(UUID serviceRequestId, InitiatePaymentRequest req,
                                   String idempotencyKey, UUID clienteId) {
        userRepository.findById(clienteId).ifPresent(user -> {
            if (user.getCpfHash() == null) {
                throw new BusinessException("IDENTITY_REQUIRED",
                        "Confirme sua identidade antes de pagar.");
            }
        });

        // Posse verificada SEMPRE, antes de qualquer ramo. Antes só o caminho de criação
        // conferia: um hit de idempotência devolvia a transação (valores + status) sem
        // checar dono nenhum, então uma colisão de chave vazava dados de outro cliente.
        requestRepository.findByIdAndCliente_Id(serviceRequestId, clienteId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        return transactionRepository
                .findByServiceRequestIdAndIdempotencyKey(serviceRequestId, idempotencyKey)
                .map(TransactionDto::from)
                .orElseGet(() -> criar(serviceRequestId, req, idempotencyKey, clienteId));
    }

    /**
     * Processa confirmação do gateway via webhook.
     * Estado financeiro dirigido por evento confirmado — não por transação de banco.
     *
     * <p>Achado da revisão cruzada (2026-10-05): {@code ServiceExecutionService.cancel} pode cancelar o pedido
     * ENQUANTO um pagamento já está a caminho (cobrança enfileirada no outbox antes do cancelamento, confirmação
     * chega depois) — nesse instante `cancel()` não encontra transação RETIDA (ela ainda nem existe) e não cria
     * reembolso nenhum. Sem esta checagem, o dinheiro ficaria retido para sempre num pedido já CANCELADO. Este é
     * o evento confirmado que dirige o estado financeiro (princípio do CLAUDE.md), então é AQUI que a
     * reconciliação acontece: confirmado o pagamento de um pedido já cancelado, retém e devolve na mesma hora.
     *
     * <p>2ª rodada (auto-revisão, 2026-10-09), duas correções na própria reconciliação:
     * <ul>
     *   <li><b>Só reconcilia na transição PENDENTE → RETIDO</b> (a 1ª confirmação). Reentrega do webhook — comum — achava a
     *       transação REEMBOLSADA e estourava {@code INVALID_PAYMENT_TRANSITION} (422 ao gateway, que reentrega para
     *       sempre); e, com a transação ainda RETIDA por um {@code cancel()} que já enfileirou o reembolso, a reconciliação
     *       enfileiraria um SEGUNDO {@code PAYMENT_REFUNDED}. Confirmação de transação que já não está PENDENTE é no-op.</li>
     *   <li><b>O pedido é travado antes de a transação ser lida.</b> Serializa com {@code cancel()} (que não via a transação
     *       RETIDA se a confirmação ainda não tivesse commitado e deixava o dinheiro retido num pedido cancelado) e com uma
     *       entrega duplicada simultânea do mesmo webhook (as duas veriam PENDENTE e enfileirariam dois reembolsos). A
     *       transação só é carregada depois da trava, então o estado que se lê é o de agora.</li>
     * </ul>
     */
    @Transactional
    public void confirmPayment(String gatewayTransactionId, String status) {
        Optional<UUID> srId = transactionRepository.findServiceRequestIdByGatewayTransactionId(gatewayTransactionId);
        if (srId.isEmpty()) {
            return;
        }
        ServiceRequest sr = requestRepository.findByIdComTrava(srId.get()).orElse(null);
        transactionRepository.findByGatewayTransactionId(gatewayTransactionId).ifPresent(tx -> {
            // REJEITADO: mantém PENDENTE para retry idempotente pelo OutboxProcessor
            if (!"PAGO".equalsIgnoreCase(status) || tx.getStatusPagamento() != TransactionStatus.PENDENTE) {
                return;
            }
            tx.reter();
            if (sr != null && sr.getStatus() == ServiceRequestStatus.CANCELADO) {
                tx.reembolsar();
                outboxRepository.save(outboxEventReembolso(tx));
            }
            transactionRepository.save(tx);
        });
    }

    /** Mesmo formato de payload que ServiceExecutionService usa para PAYMENT_REFUNDED — o OutboxProcessor é um só. */
    private static OutboxEvent outboxEventReembolso(Transaction tx) {
        String payload = String.format(
                "{\"transactionId\":\"%s\",\"serviceRequestId\":\"%s\"}", tx.getId(), tx.getServiceRequestId());
        return new OutboxEvent("transaction", tx.getId(), "PAYMENT_REFUNDED", payload);
    }

    private TransactionDto criar(UUID serviceRequestId, InitiatePaymentRequest req,
                                  String idempotencyKey, UUID clienteId) {
        // C-2: findByIdAndCliente_Id garante ownership — SR de outro cliente retorna 404
        ServiceRequest sr = requestRepository.findByIdAndCliente_Id(serviceRequestId, clienteId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (sr.getStatus() != ServiceRequestStatus.ACEITO) {
            throw new BusinessException("PAYMENT_NOT_ALLOWED",
                    "Pagamento só pode ser iniciado em pedido ACEITO.");
        }

        BigDecimal valorTotal = proposalRepository
                .findByServiceRequestIdAndStatus(serviceRequestId, ProposalStatus.ACEITA)
                .stream().findFirst()
                .map(p -> p.getValor())
                .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND",
                        "Proposta aceita não encontrada para calcular o valor."));

        // Em centavos, meio pra cima: é o que o NUMERIC(12,2) do banco faria ao gravar, e é a
        // regra que o app do prestador usa pra mostrar "Você recebe" (valorAposComissao).
        BigDecimal valorComissao = valorTotal.multiply(percentualComissao)
                .setScale(2, RoundingMode.HALF_UP);
        PaymentMethod metodo     = PaymentMethod.valueOf(req.metodo().toUpperCase());

        // Escrita atômica: Transaction + OutboxEvent na mesma transação de banco
        Transaction tx = new Transaction(serviceRequestId, valorTotal, valorComissao,
                percentualComissao, metodo, idempotencyKey);
        transactionRepository.save(tx);

        // C-3: String.format ao invés de concatenação manual para evitar JSON mal-formado
        String payload = String.format(
                "{\"transactionId\":\"%s\",\"metodo\":\"%s\"}", tx.getId(), metodo);
        OutboxEvent event = new OutboxEvent("transaction", tx.getId(), "PAYMENT_INITIATED", payload);
        outboxRepository.save(event);

        return TransactionDto.from(tx);
    }
}
