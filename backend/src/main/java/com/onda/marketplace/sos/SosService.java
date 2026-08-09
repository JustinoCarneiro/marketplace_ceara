package com.onda.marketplace.sos;

import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@SuppressWarnings("null")
public class SosService {

    private static final Logger log = LoggerFactory.getLogger(SosService.class);

    private final SosAlertRepository       alertRepository;
    private final OutboxEventRepository    outboxRepository;
    private final NotificationService      notificationService;
    private final ServiceRequestRepository srRepository;

    public SosService(SosAlertRepository alertRepository,
                      OutboxEventRepository outboxRepository,
                      NotificationService notificationService,
                      ServiceRequestRepository srRepository) {
        this.alertRepository     = alertRepository;
        this.outboxRepository    = outboxRepository;
        this.notificationService = notificationService;
        this.srRepository        = srRepository;
    }

    /**
     * Persiste alerta + OutboxEvent(SOS_TRIGGERED) atomicamente.
     * O OutboxProcessor despacha a notificação ao admin fora de @Transactional.
     */
    @Transactional
    public SosAlertDto acionarSos(UUID userId, AcionarSosRequest req) {
        // O acionamento NUNCA é recusado por validação — é botão de pânico: recusar um SOS
        // de quem está em risco seria pior que qualquer dado errado. Mas um vínculo que não
        // confere também não é propagado: iria apontar o admin para o pedido de terceiros
        // no meio de uma emergência. Sem vínculo confiável, guarda só quem/onde/quando.
        UUID serviceRequestId = vinculoConfiavel(userId, req.serviceRequestId());

        SosAlert alert = new SosAlert(userId, serviceRequestId, req.latitude(), req.longitude());
        alertRepository.save(alert);

        String payload = String.format(
                "{\"userId\":\"%s\",\"serviceRequestId\":\"%s\",\"lat\":%s,\"lng\":%s}",
                userId, serviceRequestId, req.latitude(), req.longitude());
        outboxRepository.save(new OutboxEvent("sos_alert", alert.getId(), "SOS_TRIGGERED", payload));

        // Só registra no painel aqui. A entrega externa é do OutboxProcessor: no SOS ela
        // precisa ser durável (o evento fica PENDENTE até entregar, e vira FALHA visível
        // se não conseguir), não uma tentativa best-effort que some num log.
        notificationService.registrarAlerta("SOS", alert.getId());

        return SosAlertDto.from(alert);
    }

    /** Devolve o pedido informado só se o usuário de fato participa dele; senão, null. */
    private UUID vinculoConfiavel(UUID userId, UUID serviceRequestId) {
        if (serviceRequestId == null) return null;
        if (srRepository.isParticipante(serviceRequestId, userId)) return serviceRequestId;
        log.warn("SOS acionado por {} citando pedido {} do qual não participa — alerta "
                + "registrado sem o vínculo.", userId, serviceRequestId);
        return null;
    }

    @Transactional
    public void resolver(UUID alertId) {
        SosAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException("SOS_NOT_FOUND", "Alerta SOS não encontrado."));
        alert.resolver();
        alertRepository.save(alert);
    }
}
