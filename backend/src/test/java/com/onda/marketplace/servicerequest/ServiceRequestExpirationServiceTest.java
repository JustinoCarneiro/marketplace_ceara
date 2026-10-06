package com.onda.marketplace.servicerequest;

import com.onda.marketplace.proposal.ProposalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Expiração de pedido sem prestador: o que entra na consulta, o prazo e o encerramento das propostas. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ServiceRequestExpirationServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Mock ServiceRequestRepository requestRepository;
    @Mock ProposalRepository       proposalRepository;

    ServiceRequestExpirationService service() {
        return new ServiceRequestExpirationService(requestRepository, proposalRepository, 15);
    }

    @Test
    void sohPedidosSemPrestador_PendenteEProposto_comOPrazoConfigurado() {
        when(requestRepository.idsSemAndamentoDesde(any(), any())).thenReturn(List.of());

        service().expirar(AGORA);

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(requestRepository).idsSemAndamentoDesde(
                org.mockito.ArgumentMatchers.eq(Set.of(ServiceRequestStatus.PENDENTE, ServiceRequestStatus.PROPOSTO)),
                limite.capture());
        // ACEITO/EM_ANDAMENTO têm dinheiro e prestador: nunca expiram sozinhos
        assertThat(limite.getValue()).isEqualTo(AGORA.minus(Duration.ofDays(15)));
    }

    @Test
    void semParados_naoMexeEmNada() {
        when(requestRepository.idsSemAndamentoDesde(any(), any())).thenReturn(List.of());

        assertThat(service().expirar(AGORA)).isZero();

        verify(proposalRepository, never()).encerrarAtivasDosPedidos(anyCollection());
        verify(requestRepository, never()).cancelarSemAndamento(anyCollection(), any(), any());
    }

    @Test
    void encerraAsPropostasEOsPedidos_cravandoOInstante_edizQuantosCancelou() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.idsSemAndamentoDesde(any(), any())).thenReturn(ids);
        when(requestRepository.idsComStatus(ids, ServiceRequestStatus.CANCELADO)).thenReturn(ids);

        int cancelados = service().expirar(AGORA);

        assertThat(cancelados).isEqualTo(2);
        // o UPDATE roda ANTES (decide quem foi cancelado de verdade); encerrarAtivasDosPedidos só sobre o
        // resultado dele (idsComStatus), nunca sobre a lista antiga da consulta — ver o teste da corrida abaixo
        verify(requestRepository).cancelarSemAndamento(eq(ids), any(), eq(AGORA));   // o UPDATE em lote não roda o @PreUpdate
        verify(proposalRepository).encerrarAtivasDosPedidos(ids);
    }

    @Test
    void contaSohOQueFoiDeFatoCancelado_eSoEncerraAsPropostasDessesMesmos() {
        // Achado da revisão cruzada (2026-10-05): uma proposta nova chegou a tempo (entre a consulta e o UPDATE)
        // pra UM dos três candidatos — ele não é cancelado (o UPDATE guardado não o afeta) e a proposta dele não
        // pode ser encerrada, mesmo estando na lista original da consulta.
        UUID escapou = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), escapou);
        List<UUID> cancelados = List.of(ids.get(0), ids.get(1));   // só estes dois; "escapou" ficou de fora
        when(requestRepository.idsSemAndamentoDesde(any(), any())).thenReturn(ids);
        when(requestRepository.idsComStatus(ids, ServiceRequestStatus.CANCELADO)).thenReturn(cancelados);

        assertThat(service().expirar(AGORA)).isEqualTo(2);

        verify(proposalRepository).encerrarAtivasDosPedidos(cancelados);
        verify(proposalRepository, never()).encerrarAtivasDosPedidos(ids);   // nunca a lista inteira (tem o "escapou")
    }

    @Test
    void muitosParados_vaoEmLotesDeNoMaximo500() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 1200; i++) ids.add(UUID.randomUUID());
        when(requestRepository.idsSemAndamentoDesde(any(), any())).thenReturn(ids);
        when(requestRepository.idsComStatus(anyCollection(), any())).thenAnswer(i -> i.getArgument(0));

        service().expirar(AGORA);

        ArgumentCaptor<java.util.Collection<UUID>> lotes = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(proposalRepository, times(3)).encerrarAtivasDosPedidos(lotes.capture());
        assertThat(lotes.getAllValues()).extracting(java.util.Collection::size).containsExactly(500, 500, 200);
    }

    @Test
    void ojobDelegaEFicaAgendado() throws Exception {
        PedidoExpiracaoJob job = new PedidoExpiracaoJob(service());
        when(requestRepository.idsSemAndamentoDesde(any(), any())).thenReturn(List.of());

        job.expirarParados();

        verify(requestRepository).idsSemAndamentoDesde(any(), any());
        // sem a anotação o job nunca rodaria — e nenhum teste de Mockito perceberia
        assertThat(PedidoExpiracaoJob.class.getMethod("expirarParados").isAnnotationPresent(Scheduled.class)).isTrue();
    }
}
