package com.onda.marketplace.notification;

import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Cria um alerta operacional para o admin.
     * O envio de e-mail/push é feito após o commit da persistência.
     *
     * @param tipo  {@code SOS} | {@code DISPUTA} | {@code VERIFICACAO}
     * @param refId UUID do registro de origem (nunca CPF — TS04/LGPD)
     */
    @Transactional
    public AdminNotificationDto criarAlerta(String tipo, UUID refId) {
        AdminNotification notif = new AdminNotification(tipo, refId);
        repository.save(notif);
        // Envio externo ocorre após o save — falha não afeta a transação
        enviarCanaisExternos(tipo, refId);
        return AdminNotificationDto.from(notif);
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

    // --- privado ---

    private void enviarCanaisExternos(String tipo, UUID refId) {
        emailSender.enviar(tipo, refId);
        pushSender.enviar(tipo, refId);
    }
}
