package com.onda.marketplace.proposal;

import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProposalServiceTest {

    @Mock ProposalRepository       proposalRepository;
    @Mock ServiceRequestRepository requestRepository;

    ProposalService service;

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProposalService(proposalRepository, requestRepository);
    }

    @Test
    void create_pedidoPendente_transicionaParaProposto() {
        var sr = serviceRequest(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findById(sr.getId())).thenReturn(Optional.of(sr));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.create(sr.getId(), new CreateProposalRequest(BigDecimal.valueOf(200), 2), UUID.randomUUID());

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.PROPOSTO);
        verify(requestRepository).save(sr);
    }

    @Test
    void accept_transicionaParaAceito_eFechaOutras() {
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        var propAlvo = proposal(sr, ProposalStatus.ATIVA);
        var propOutra = proposal(sr, ProposalStatus.ATIVA);

        when(proposalRepository.findById(propAlvo.getId())).thenReturn(Optional.of(propAlvo));
        when(proposalRepository.findByServiceRequestIdAndStatus(sr.getId(), ProposalStatus.ATIVA))
                .thenReturn(List.of(propAlvo, propOutra));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProposalDto dto = service.accept(propAlvo.getId(), CLIENTE_ID);

        assertThat(dto.status()).isEqualTo("ACEITA");
        assertThat(propOutra.getStatus()).isEqualTo(ProposalStatus.ENCERRADA);
        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.ACEITO);
    }

    @Test
    void reject_marcaComoRecusada() {
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        var prop = proposal(sr, ProposalStatus.ATIVA);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProposalDto dto = service.reject(prop.getId(), CLIENTE_ID);

        assertThat(dto.status()).isEqualTo("RECUSADA");
    }

    @Test
    void create_pedidoNaoExistente_lancaBusinessException() {
        UUID randomId = UUID.randomUUID();
        when(requestRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(randomId, new CreateProposalRequest(BigDecimal.valueOf(100), 1), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");
    }

    // helpers
    private ServiceRequest serviceRequest(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        return sr;
    }

    private Proposal proposal(ServiceRequest sr, ProposalStatus status) {
        return new Proposal(sr, UUID.randomUUID(), BigDecimal.valueOf(200), 2, status);
    }
}
