package com.onda.marketplace.notification;

import com.onda.marketplace.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Central de notificações do painel admin (M12/US30).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Persistir o alerta em {@code admin_notifications}.</li>
 *   <li>Disparar e-mail via {@link EmailSender} (falha não é fatal).</li>
 *   <li>Disparar push via {@link PushSender} (NoOp no MVP).</li>
 *   <li>Listar notificações com filtro opcional por {@code lida}.</li>
 *   <li>Marcar uma notificação como lida.</li>
 * </ul>
 *
 * <p>O envio de e-mail/push ocorre FORA do {@code @Transactional} da persistência
 * para respeitar o princípio Saga/Outbox: a chamada externa nunca envolve
 * a transação de banco. A falha de envio jamais reverte o alerta persistido.
 */
@Service
@SuppressWarnings("null")
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AdminNotificationRepository repository;
    private final EmailSender                 emailSender;
    private final PushSender                  pushSender;

    public NotificationService(AdminNotificationRepository repository,
                               EmailSender emailSender,
                               PushSender pushSender) {
        this.repository  = repository;
        this.emailSender = emailSender;
        this.pushSender  = pushSender;
    }

    /**
     * Cria um alerta operacional e tenta entregá-lo pelos canais externos.
     *
     * <p>A entrega é agendada para <b>depois do commit</b>. Antes ela acontecia dentro do
     * {@code @Transactional} (apesar do javadoc afirmar o contrário): um SMTP lento segurava
     * a transação de banco e a requisição HTTP — no SOS, isso é o usuário em emergência
     * esperando o timeout do servidor de e-mail.
     *
     * <p>Entrega best-effort: a falha é registrada, não propagada — o alerta já está
     * persistido e aparece no painel. Para entrega com garantia use
     * {@link #registrarAlerta} + Outbox, como faz o SOS.
     *
     * @param tipo  {@code SOS} | {@code DISPUTA} | {@code VERIFICACAO}
     * @param refId UUID do registro de origem (nunca CPF — TS04/LGPD)
     */
    @Transactional
    public AdminNotificationDto criarAlerta(String tipo, UUID refId) {
        AdminNotificationDto dto = registrarAlerta(tipo, refId);
        aposCommit(() -> {
            try {
                entregar(tipo, refId);
            } catch (NotificationDeliveryException ex) {
                log.error("Alerta {} (ref={}) persistido, mas não entregue: {}",
                        tipo, refId, ex.getMessage());
            }
        });
        return dto;
    }

    /**
     * Só persiste o alerta, sem tentar entregar. Para quando a entrega é responsabilidade
     * de outro mecanismo — no SOS, do Outbox, que dá durabilidade e retry (US30: o alerta
     * de SOS não pode depender de uma tentativa única e best-effort).
     */
    @Transactional
    public AdminNotificationDto registrarAlerta(String tipo, UUID refId) {
        AdminNotification notif = new AdminNotification(tipo, refId);
        repository.save(notif);
        return AdminNotificationDto.from(notif);
    }

    /**
     * Entrega o alerta pelos canais externos. Chamado pelo {@code OutboxProcessor} no caso
     * do SOS — fora de qualquer transação de banco, como manda o princípio Saga/Outbox.
     *
     * @throws NotificationDeliveryException se algum canal ativo falhou
     */
    public void entregar(String tipo, UUID refId) {
        emailSender.enviar(tipo, refId);
        pushSender.enviar(tipo, refId);
    }

    /**
     * Lista notificações do painel.
     *
     * @param lida {@code true} = lidas, {@code false} = não lidas, {@code null} = todas
     */
    @Transactional(readOnly = true)
    public List<AdminNotificationDto> listar(Boolean lida) {
        List<AdminNotification> lista = (lida == null)
                ? repository.findAllByOrderByCriadoEmDesc()
                : repository.findByLidaOrderByCriadoEmDesc(lida);
        return lista.stream().map(AdminNotificationDto::from).toList();
    }

    /**
     * Marca uma notificação como lida.
     */
    @Transactional
    public void marcarLida(UUID id) {
        AdminNotification notif = repository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND",
                        "Notificação não encontrada."));
        notif.marcarLida();
        repository.save(notif);
    }

    /**
     * Marca todas as notificações não lidas como lidas (US30).
     *
     * @return quantidade de notificações afetadas
     */
    @Transactional
    public int marcarTodasLidas() {
        return repository.marcarTodasLidas();
    }

    // --- privado ---

    /**
     * Executa depois do commit; sem transação ativa (chamada avulsa, teste), roda na hora.
     */
    private void aposCommit(Runnable acao) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            acao.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { acao.run(); }
        });
    }
}
