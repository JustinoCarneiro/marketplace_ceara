package com.onda.marketplace.servicerequest;

import com.onda.marketplace.proposal.ProposalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Expiração de pedido sem prestador: PENDENTE ou PROPOSTO que fica {@code marketplace.request.expiration-days} (15)
 * dias sem andamento — nenhuma mudança de estado e nenhuma proposta nova — vai para CANCELADO e suas propostas
 * ainda abertas se encerram. Sem isso o pedido esquecido ficava na fila (ou preso em PROPOSTO) para sempre.
 * Não há dinheiro envolvido nesses estados: ele só entra no aceite.
 */
@Service
public class ServiceRequestExpirationService {

    private static final Set<ServiceRequestStatus> SEM_PRESTADOR =
            EnumSet.of(ServiceRequestStatus.PENDENTE, ServiceRequestStatus.PROPOSTO);
    /** Tamanho do IN: poucas centenas por vez, para a lista de parâmetros não crescer sem limite. */
    private static final int LOTE = 500;

    private final ServiceRequestRepository requestRepository;
    private final ProposalRepository       proposalRepository;
    private final Duration                 prazo;

    public ServiceRequestExpirationService(ServiceRequestRepository requestRepository,
                                           ProposalRepository proposalRepository,
                                           @Value("${marketplace.request.expiration-days:15}") long dias) {
        this.requestRepository = requestRepository;
        this.proposalRepository = proposalRepository;
        this.prazo = Duration.ofDays(dias);
    }

    /** @return quantos pedidos foram cancelados por falta de andamento */
    @Transactional
    public int expirar(Instant agora) {
        List<UUID> parados = requestRepository.idsSemAndamentoDesde(SEM_PRESTADOR, agora.minus(prazo));
        int cancelados = 0;
        for (int i = 0; i < parados.size(); i += LOTE) {
            List<UUID> lote = parados.subList(i, Math.min(i + LOTE, parados.size()));
            proposalRepository.encerrarAtivasDosPedidos(lote);
            cancelados += requestRepository.cancelarSemAndamento(lote, agora);
        }
        return cancelados;
    }
}
