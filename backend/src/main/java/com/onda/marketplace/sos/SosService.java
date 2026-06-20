package com.onda.marketplace.sos;

import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SosService {

    private final SosAlertRepository    alertRepository;
    private final OutboxEventRepository outboxRepository;

    public SosService(SosAlertRepository alertRepository, OutboxEventRepository outboxRepository) {
        this.alertRepository  = alertRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Persiste alerta + OutboxEvent(SOS_TRIGGERED) atomicamente.
     * O OutboxProcessor despacha a notificação ao admin fora de @Transactional.
     */
    @Transactional
    public SosAlertDto acionarSos(UUID userId, AcionarSosRequest req) {
        SosAlert alert = new SosAlert(userId, req.serviceRequestId(), req.latitude(), req.longitude());
        alertRepository.save(alert);

        String payload = String.format(
                "{\"userId\":\"%s\",\"serviceRequestId\":\"%s\",\"lat\":%s,\"lng\":%s}",
                userId, req.serviceRequestId(), req.latitude(), req.longitude());
        outboxRepository.save(new OutboxEvent("sos_alert", alert.getId(), "SOS_TRIGGERED", payload));

        return SosAlertDto.from(alert);
    }

    @Transactional
    public void resolver(UUID alertId) {
        SosAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException("SOS_NOT_FOUND", "Alerta SOS não encontrado."));
        alert.resolver();
        alertRepository.save(alert);
    }
}
