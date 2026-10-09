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

    /**
     * @return quantos pedidos foram cancelados por falta de andamento
     *
     * <p>Achado da revisão cruzada (2026-10-05): o UPDATE confere a condição completa de novo (ver
     * {@code ServiceRequestRepository.cancelarSemAndamento}) — não só vai primeiro, mas é ele quem decide quais
     * pedidos foram REALMENTE cancelados. {@code encerrarAtivasDosPedidos} só roda sobre esse subconjunto
     * ({@code idsCanceladosEm}, lido DEPOIS, na mesma transação, só o que ESTE UPDATE gravou): antes, rodava sobre o lote inteiro (a lista
     * antiga, da consulta), então uma proposta nova — chegada bem a tempo de salvar o pedido da expiração —
     * podia ser encerrada mesmo assim, deixando o pedido PROPOSTO sem nenhuma proposta ativa (a mesma lacuna que
     * a fila ter voltado a PENDENTE existe pra evitar).
     */
    @Transactional
    public int expirar(Instant instante) {
        // Microssegundos, a precisão da coluna: o UPDATE grava este instante e idsCanceladosEm o compara por igualdade.
        Instant agora = instante.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant limite = agora.minus(prazo);
        List<UUID> parados = requestRepository.idsSemAndamentoDesde(SEM_PRESTADOR, limite);
        int cancelados = 0;
        for (int i = 0; i < parados.size(); i += LOTE) {
            List<UUID> lote = parados.subList(i, Math.min(i + LOTE, parados.size()));
            requestRepository.cancelarSemAndamento(lote, limite, agora);
            List<UUID> efetivamenteCancelados = requestRepository.idsCanceladosEm(lote, agora);
            proposalRepository.encerrarAtivasDosPedidos(efetivamenteCancelados);
            cancelados += efetivamenteCancelados.size();
        }
        return cancelados;
    }
}
