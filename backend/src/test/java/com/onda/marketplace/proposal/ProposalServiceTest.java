package com.onda.marketplace.proposal;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderProfiles;
import com.onda.marketplace.provider.ProviderStatus;
import com.onda.marketplace.provider.ProviderVerificationGuard;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProposalServiceTest {

    @Mock ProposalRepository        proposalRepository;
    @Mock ServiceRequestRepository  requestRepository;
    @Mock UserRepository            userRepository;
    @Mock ProviderProfileRepository profileRepository;
    @Mock ReviewRepository          reviewRepository;

    ProposalService service;

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // A regra de verificação é real (sobre o repositório mockado): um mock dela aceitaria
        // qualquer prestador e esses testes não provariam nada.
        service = new ProposalService(proposalRepository, requestRepository,
                userRepository, profileRepository, reviewRepository,
                new ProviderVerificationGuard(profileRepository));
    }

    private void prestadorCom(UUID prestadorId, ProviderStatus status) {
        when(profileRepository.findByUserId(prestadorId))
                .thenReturn(Optional.of(ProviderProfiles.comStatus(status)));
    }

    @Test
    void create_pedidoPendente_transicionaParaProposto() {
        var sr = serviceRequest(ServiceRequestStatus.PENDENTE);
        UUID prestadorId = UUID.randomUUID();
        prestadorCom(prestadorId, ProviderStatus.VERIFICADO);
        when(requestRepository.findById(sr.getId())).thenReturn(Optional.of(sr));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.create(sr.getId(),
                new CreateProposalRequest(BigDecimal.valueOf(200), 2, Instant.now().plusSeconds(3600)),
                prestadorId);

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.PROPOSTO);
        verify(requestRepository).save(sr);
    }

    @ParameterizedTest
    @EnumSource(value = ProviderStatus.class, names = "VERIFICADO", mode = EnumSource.Mode.EXCLUDE)
    void create_prestadorNaoVerificado_recusaESemEfeitos(ProviderStatus status) {
        // A "aprovação manual" do admin só mudava o status: prestador em verificação, reprovado ou
        // suspenso mandava proposta e, aceita, recebia. Agora a proposta nem é gravada.
        var sr = serviceRequest(ServiceRequestStatus.PENDENTE);
        UUID prestadorId = UUID.randomUUID();
        prestadorCom(prestadorId, status);
        when(requestRepository.findById(sr.getId())).thenReturn(Optional.of(sr));

        assertThatThrownBy(() ->
                service.create(sr.getId(),
                        new CreateProposalRequest(BigDecimal.valueOf(200), 2, Instant.now().plusSeconds(3600)),
                        prestadorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED");

        // nada muda: sem proposta e o pedido NÃO vira PROPOSTO por causa de uma proposta recusada
        verify(proposalRepository, never()).save(any());
        verify(requestRepository, never()).save(any());
        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.PENDENTE);
    }

    @Test
    void create_prestadorSemPerfil_recusaComoNaoVerificado() {
        var sr = serviceRequest(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findById(sr.getId())).thenReturn(Optional.of(sr));
        // profileRepository sem stub: Optional.empty()

        assertThatThrownBy(() ->
                service.create(sr.getId(),
                        new CreateProposalRequest(BigDecimal.valueOf(200), 2, Instant.now().plusSeconds(3600)),
                        UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void create_pedidoJaAceito_lancaRequestClosed() {
        // US15: só PENDENTE/PROPOSTO aceitam proposta nova — antes, ACEITO/EM_ANDAMENTO/
        // EM_DISPUTA passavam batido e dava pra empilhar proposta num serviço em execução.
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findById(sr.getId())).thenReturn(Optional.of(sr));

        assertThatThrownBy(() ->
                service.create(sr.getId(),
                        new CreateProposalRequest(BigDecimal.valueOf(200), 2, Instant.now().plusSeconds(3600)),
                        UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_CLOSED");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void accept_transicionaParaAceito_eFechaOutras() {
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        var propAlvo = proposal(sr, ProposalStatus.ATIVA);
        var propOutra = proposal(sr, ProposalStatus.ATIVA);

        prestadorCom(propAlvo.getPrestadorId(), ProviderStatus.VERIFICADO);
        when(proposalRepository.findById(propAlvo.getId())).thenReturn(Optional.of(propAlvo));
        when(proposalRepository.findByServiceRequestIdAndStatus(sr.getId(), ProposalStatus.ATIVA))
                .thenReturn(List.of(propAlvo, propOutra));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProposalDto dto = service.accept(propAlvo.getId(), CLIENTE_ID);

        assertThat(dto.status()).isEqualTo("ACEITA");
        assertThat(propOutra.getStatus()).isEqualTo(ProposalStatus.ENCERRADA);
        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.ACEITO);
    }

    @ParameterizedTest
    @EnumSource(value = ProviderStatus.class, names = "VERIFICADO", mode = EnumSource.Mode.EXCLUDE)
    void accept_prestadorDeixouDeSerVerificado_recusaESemEfeitos(ProviderStatus status) {
        // O status pode mudar entre a proposta e o aceite (admin reprova/suspende). Sem esta checagem
        // o cliente pagaria — e o repasse sairia — para um prestador que o admin já barrou.
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        var propAlvo = proposal(sr, ProposalStatus.ATIVA);
        prestadorCom(propAlvo.getPrestadorId(), status);
        when(proposalRepository.findById(propAlvo.getId())).thenReturn(Optional.of(propAlvo));

        assertThatThrownBy(() -> service.accept(propAlvo.getId(), CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED")
                .hasMessageContaining("Escolha outra proposta");

        // recusa antes de qualquer efeito: nem fecha as outras propostas, nem muda o pedido
        verify(proposalRepository, never()).findByServiceRequestIdAndStatus(any(), any());
        verify(proposalRepository, never()).save(any());
        verify(requestRepository, never()).save(any());
        assertThat(propAlvo.getStatus()).isEqualTo(ProposalStatus.ATIVA);
        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.PROPOSTO);
    }

    @Test
    void accept_naoEhOClienteDoPedido_lancaForbidden() {
        // Antes, clienteId nunca era conferido contra o dono real do pedido — qualquer
        // conta autenticada aceitava proposta de pedido alheio.
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        var prop = proposal(sr, ProposalStatus.ATIVA);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));

        UUID alheio = UUID.randomUUID();
        assertThatThrownBy(() -> service.accept(prop.getId(), alheio))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void accept_prestadorTentaAceitarOProprioPedido_lancaSelfHireForbidden() {
        // Antifraude Camada 1 (PENDENCIAS_INTEGRIDADE.md): impede auto-contratação — sem
        // isto, o mesmo usuário (dono do pedido = prestador da proposta) fabrica reputação.
        // No self-hire de verdade, quem aceita É o dono do pedido — por isso sr.cliente
        // também recebe o id do "prestador" aqui, senão o check de posse (novo) dispararia
        // FORBIDDEN antes de chegar no SELF_HIRE_FORBIDDEN que este teste quer travar.
        UUID prestadorId = UUID.randomUUID();
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        setClienteId(sr, prestadorId);
        var prop = new Proposal(sr, prestadorId, BigDecimal.valueOf(200), 2, null, ProposalStatus.ATIVA);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));

        assertThatThrownBy(() -> service.accept(prop.getId(), prestadorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_HIRE_FORBIDDEN");
        verify(proposalRepository, never()).save(any());
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
    void reject_naoEhOClienteDoPedido_lancaForbidden() {
        // Antes, o parâmetro clienteId chegava até aqui e nunca era usado — qualquer conta
        // autenticada recusava proposta de prestador em pedido alheio.
        var sr = serviceRequest(ServiceRequestStatus.PROPOSTO);
        var prop = proposal(sr, ProposalStatus.ATIVA);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));

        UUID alheio = UUID.randomUUID();
        assertThatThrownBy(() -> service.reject(prop.getId(), alheio))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void create_pedidoNaoExistente_lancaBusinessException() {
        UUID randomId = UUID.randomUUID();
        when(requestRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(randomId,
                        new CreateProposalRequest(BigDecimal.valueOf(100), 1, Instant.now().plusSeconds(3600)),
                        UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");
    }

    // helpers
    private ServiceRequest serviceRequest(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        setClienteId(sr, CLIENTE_ID);
        return sr;
    }

    private Proposal proposal(ServiceRequest sr, ProposalStatus status) {
        return new Proposal(sr, UUID.randomUUID(), BigDecimal.valueOf(200), 2, null, status);
    }

    /** Constrói um cliente com id fixo (User.id é @GeneratedValue) e associa ao pedido. */
    private static void setClienteId(ServiceRequest sr, UUID clienteId) {
        User cliente = User.builder()
                .nome("Cliente Teste").email("cliente@test.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(cliente, clienteId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        sr.setCliente(cliente);
    }
}
