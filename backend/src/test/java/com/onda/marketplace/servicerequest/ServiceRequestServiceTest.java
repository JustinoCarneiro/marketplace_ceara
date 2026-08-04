package com.onda.marketplace.servicerequest;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ServiceRequestServiceTest {

    @Mock ServiceRequestRepository    requestRepository;
    @Mock ServiceMediaRepository      mediaRepository;
    @Mock UserRepository              userRepository;
    @Mock AiSuggestionService         aiService;
    @Mock StorageService              storageService;
    @Mock ProposalRepository          proposalRepository;
    @Mock TransactionRepository       transactionRepository;

    ServiceRequestService service;

    private final User cliente = User.builder()
            .nome("João")
            .email("joao@test.com")
            .senhaHash("$2a$hash")
            .role(UserRole.ROLE_CLIENT)
            .build();

    @BeforeEach
    void setUp() {
        service = new ServiceRequestService(requestRepository, mediaRepository, userRepository, aiService,
                storageService, proposalRepository, transactionRepository);
    }

    @Test
    void create_comSugestaoIA_retornaDescricaoSugerida() {
        var suggestion = new AiSuggestion("Instalação de chuveiro elétrico", BigDecimal.valueOf(150), BigDecimal.valueOf(300));
        when(userRepository.findById(any())).thenReturn(Optional.of(cliente));
        when(aiService.suggest(any(), any())).thenReturn(Optional.of(suggestion));
        when(requestRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new CreateServiceRequestRequest("ELETRICISTA", "Chuveiro sem funcionar", -3.7319, -38.5267);
        ServiceRequestDto dto = service.create(UUID.randomUUID(), req, "idem-key-1");

        assertThat(dto.aiDescricaoSugerida()).isEqualTo("Instalação de chuveiro elétrico");
        assertThat(dto.status()).isEqualTo("PENDENTE");
    }

    @Test
    void create_iaFalha_retornaSemSugestao() {
        when(userRepository.findById(any())).thenReturn(Optional.of(cliente));
        when(aiService.suggest(any(), any())).thenReturn(Optional.empty());  // IA indisponível
        when(requestRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new CreateServiceRequestRequest("ENCANADOR", null, -3.7319, -38.5267);
        ServiceRequestDto dto = service.create(UUID.randomUUID(), req, "idem-key-2");

        // Pedido criado normalmente — sem sugestão IA (fallback manual)
        assertThat(dto.status()).isEqualTo("PENDENTE");
        assertThat(dto.aiDescricaoSugerida()).isNull();
        verify(requestRepository).save(any());
    }

    @Test
    void create_chaveIdempotenteDuplicada_retornaExistente() {
        var existing = new ServiceRequest();
        existing.setCategoria("ELETRICISTA");
        existing.setStatus(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

        var req = new CreateServiceRequestRequest("ELETRICISTA", null, -3.7, -38.5);
        ServiceRequestDto dto = service.create(UUID.randomUUID(), req, "idem-dup");

        assertThat(dto.status()).isEqualTo("PENDENTE");
        verify(requestRepository, never()).save(any());  // não salva de novo
    }

    @Test
    void detalhar_participante_retornaDetalheComPrestador() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID prestadorId = UUID.randomUUID();

        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setCategoria("ELETRICISTA");
        sr.setDescricao("Chuveiro sem funcionar");
        sr.setStatus(ServiceRequestStatus.ACEITO);

        var proposta = new Proposal(sr, prestadorId, BigDecimal.valueOf(150), 2, ProposalStatus.ACEITA);
        var prestador = User.builder().nome("Zé Elétrica").email("ze@test.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(requestId, userId)).thenReturn(true);
        when(proposalRepository.findByServiceRequestIdAndStatus(requestId, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta));
        when(userRepository.findById(prestadorId)).thenReturn(Optional.of(prestador));
        when(transactionRepository.findByServiceRequestId(requestId)).thenReturn(Optional.empty());

        ServiceRequestDetailDto dto = service.detalhar(requestId, userId);

        assertThat(dto.status()).isEqualTo("ACEITO");
        assertThat(dto.prestadorId()).isEqualTo(prestadorId);
        assertThat(dto.prestadorNome()).isEqualTo("Zé Elétrica");
        assertThat(dto.transacao()).isNull();
    }

    @Test
    void detalhar_naoParticipante_lancaForbidden() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(requestId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.detalhar(requestId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("participa");
    }

    @Test
    void publicar_statusPendente_atualizaDescricaoEMantemPendente() {
        UUID requestId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setCategoria("ELETRICISTA");
        sr.setDescricao("sugestão da IA");
        sr.setStatus(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findByIdAndCliente_Id(requestId, clienteId)).thenReturn(Optional.of(sr));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ServiceRequestDto dto = service.publicar(requestId, clienteId, new PublishRequest("descrição final editada"));

        assertThat(dto.descricao()).isEqualTo("descrição final editada");
        assertThat(dto.status()).isEqualTo("PENDENTE");
    }

    @Test
    void publicar_statusJaAceito_lancaInvalidTransition() {
        UUID requestId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setStatus(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(requestId, clienteId)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() -> service.publicar(requestId, clienteId, new PublishRequest("x")))
                .isInstanceOf(BusinessException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void listarDisponiveis_retornaPedidosPendentes() {
        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setCategoria("ELETRICISTA");
        sr.setDescricao("Tomada soltando faísca");
        sr.setStatus(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findByStatusOrderByCreatedAtDesc(ServiceRequestStatus.PENDENTE))
                .thenReturn(List.of(sr));

        List<AvailableRequestDto> result = service.listarDisponiveis();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoria()).isEqualTo("ELETRICISTA");
        assertThat(result.get(0).status()).isEqualTo("PENDENTE");
    }

    @Test
    void listarMeusPedidos_contaPropostasAtivasEIncluiValorRetido() {
        UUID clienteId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setCategoria("ENCANADOR");
        sr.setStatus(ServiceRequestStatus.PROPOSTO);

        var tx = new Transaction(requestId, BigDecimal.valueOf(200), BigDecimal.valueOf(20),
                BigDecimal.TEN, PaymentMethod.PIX, "idem-x");

        when(requestRepository.findByClienteIdOrderByUpdatedAtDesc(clienteId)).thenReturn(List.of(sr));
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ATIVA)))
                .thenReturn(List.of(mock(Proposal.class), mock(Proposal.class)));
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of());
        when(transactionRepository.findByServiceRequestId(any())).thenReturn(Optional.of(tx));

        List<MyRequestDto> result = service.listarMeusPedidos(clienteId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).propostas()).isEqualTo(2);
        assertThat(result.get(0).transacao().valorTotal()).isEqualByComparingTo("200");
    }

    @Test
    void buscarPedidoAtivoDoPrestador_comAceitoEEmAndamento_retornaOMaisRecente() {
        UUID prestadorId = UUID.randomUUID();

        var srAntigo = new ServiceRequest();
        srAntigo.setCliente(cliente);
        srAntigo.setStatus(ServiceRequestStatus.ACEITO);
        setId(srAntigo, UUID.randomUUID());
        var propostaAntiga = new Proposal(srAntigo, prestadorId, BigDecimal.TEN, 1, ProposalStatus.ACEITA);

        var srRecente = new ServiceRequest();
        srRecente.setCliente(cliente);
        srRecente.setStatus(ServiceRequestStatus.EM_ANDAMENTO);
        setId(srRecente, UUID.randomUUID());
        var propostaRecente = new Proposal(srRecente, prestadorId, BigDecimal.TEN, 1, ProposalStatus.ACEITA);

        when(proposalRepository.findByPrestadorIdAndStatus(prestadorId, ProposalStatus.ACEITA))
                .thenReturn(List.of(propostaAntiga, propostaRecente));
        when(requestRepository.findById(srAntigo.getId())).thenReturn(Optional.of(srAntigo));
        when(requestRepository.findById(srRecente.getId())).thenReturn(Optional.of(srRecente));

        UUID resultado = service.buscarPedidoAtivoDoPrestador(prestadorId);

        // srRecente foi construído depois — updatedAt (Instant.now() no campo) é posterior.
        assertThat(resultado).isEqualTo(srRecente.getId());
    }

    @Test
    void buscarPedidoAtivoDoPrestador_semAtendimentoElegivel_retornaNull() {
        UUID prestadorId = UUID.randomUUID();
        var srConcluido = new ServiceRequest();
        srConcluido.setCliente(cliente);
        srConcluido.setStatus(ServiceRequestStatus.CONCLUIDO);
        setId(srConcluido, UUID.randomUUID());
        var proposta = new Proposal(srConcluido, prestadorId, BigDecimal.TEN, 1, ProposalStatus.ACEITA);

        when(proposalRepository.findByPrestadorIdAndStatus(prestadorId, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta));
        when(requestRepository.findById(srConcluido.getId())).thenReturn(Optional.of(srConcluido));

        assertThat(service.buscarPedidoAtivoDoPrestador(prestadorId)).isNull();
    }

    private static void setId(ServiceRequest sr, UUID id) {
        try {
            var field = ServiceRequest.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(sr, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
