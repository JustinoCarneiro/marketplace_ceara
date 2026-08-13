package com.onda.marketplace.message;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class MessageServiceTest {

    @Mock MessageRepository        messageRepository;
    @Mock ServiceRequestRepository requestRepository;
    @Mock UserRepository           userRepository;

    MessageService service;

    UUID srId = UUID.randomUUID();
    UUID clienteId = UUID.randomUUID();
    UUID prestadorId = UUID.randomUUID();
    UUID estranhoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MessageService(messageRepository, requestRepository, userRepository);
    }

    private ServiceRequest sr(ServiceRequestStatus status) {
        var s = new ServiceRequest();
        s.setStatus(status);
        return s;
    }

    @Test
    void enviar_participanteEmPedidoAceito_persisteMensagem() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, clienteId)).thenReturn(true);
        when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var user = User.builder().nome("Cliente Teste").email("c@t.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        when(userRepository.findById(clienteId)).thenReturn(Optional.of(user));

        MessageDto dto = service.enviar(srId, clienteId, "posso ir amanhã de manhã?");

        assertThat(dto.conteudo()).isEqualTo("posso ir amanhã de manhã?");
        assertThat(dto.mascarado()).isFalse();
        assertThat(dto.remetenteNome()).isEqualTo("Cliente Teste");
        verify(messageRepository).save(any());
    }

    @Test
    void enviar_comTelefoneNoTexto_persisteJaMascaradoENuncaGravaOOriginal() {
        var sr = sr(ServiceRequestStatus.ACEITO);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, prestadorId)).thenReturn(true);
        when(userRepository.findById(prestadorId)).thenReturn(Optional.empty());
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        when(messageRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        service.enviar(srId, prestadorId, "me chama no (85) 99999-9999");

        Message salvo = captor.getValue();
        assertThat(salvo.isMascarado()).isTrue();
        assertThat(salvo.getConteudo()).doesNotContain("99999");
        assertThat(salvo.getConteudo()).contains("[contato removido]");
    }

    @Test
    void enviar_naoParticipante_lancaForbidden() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, estranhoId)).thenReturn(false);

        assertThatThrownBy(() -> service.enviar(srId, estranhoId, "oi"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void enviar_pedidoAindaPendente_lancaChatIndisponivel() {
        // PENDENTE/PROPOSTO ainda não têm par cliente-prestador definido (pode ter zero ou
        // várias propostas concorrendo) — não faz sentido abrir chat antes do aceite.
        var sr = sr(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, clienteId)).thenReturn(true);

        assertThatThrownBy(() -> service.enviar(srId, clienteId, "oi"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CHAT_INDISPONIVEL");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void enviar_pedidoCancelado_lancaChatIndisponivel() {
        var sr = sr(ServiceRequestStatus.CANCELADO);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, clienteId)).thenReturn(true);

        assertThatThrownBy(() -> service.enviar(srId, clienteId, "oi"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CHAT_INDISPONIVEL");
    }

    @Test
    void listar_naoParticipante_lancaForbidden() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, estranhoId)).thenReturn(false);

        assertThatThrownBy(() -> service.listar(srId, estranhoId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void listar_participante_devolveMensagensEmOrdemCronologica() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        var msg1 = new Message(sr, clienteId, "primeira", false);
        var msg2 = new Message(sr, prestadorId, "segunda", false);
        when(requestRepository.findById(srId)).thenReturn(Optional.of(sr));
        when(requestRepository.isParticipante(srId, clienteId)).thenReturn(true);
        when(messageRepository.findByServiceRequestIdOrderByCreatedAtAsc(srId))
                .thenReturn(List.of(msg1, msg2));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        List<MessageDto> lista = service.listar(srId, clienteId);

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).conteudo()).isEqualTo("primeira");
        assertThat(lista.get(1).conteudo()).isEqualTo("segunda");
    }

    @Test
    void enviar_pedidoNaoExistente_lancaBusinessException() {
        when(requestRepository.findById(srId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enviar(srId, clienteId, "oi"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");
    }
}
