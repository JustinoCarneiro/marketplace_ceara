package com.onda.marketplace.message;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Chat pré-transação entre cliente e prestador de um pedido (docs/BOAS_PRATICAS_UX.md §1). */
@Service
@SuppressWarnings("null")
public class MessageService {

    // Só existe "cliente + prestador" definidos a partir do aceite — PENDENTE/PROPOSTO ainda
    // podem ter zero ou vários prestadores propondo, sem par fixo pra conversar. CANCELADO
    // fecha a conversa (relação nunca virou serviço de verdade).
    private static final Set<ServiceRequestStatus> CHAT_ATIVO = EnumSet.of(
            ServiceRequestStatus.ACEITO, ServiceRequestStatus.EM_ANDAMENTO,
            ServiceRequestStatus.CONCLUIDO, ServiceRequestStatus.EM_DISPUTA);

    private final MessageRepository        messageRepository;
    private final ServiceRequestRepository requestRepository;
    private final UserRepository           userRepository;

    public MessageService(MessageRepository messageRepository,
                          ServiceRequestRepository requestRepository,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.requestRepository = requestRepository;
        this.userRepository    = userRepository;
    }

    @Transactional
    public MessageDto enviar(UUID srId, UUID remetenteId, String conteudoBruto) {
        ServiceRequest sr = requestRepository.findById(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (!requestRepository.isParticipante(srId, remetenteId)) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }
        if (!CHAT_ATIVO.contains(sr.getStatus())) {
            throw new BusinessException("CHAT_INDISPONIVEL",
                    "O chat só fica disponível a partir do aceite da proposta.");
        }

        var resultado = ContactMaskingUtil.mascarar(conteudoBruto);
        var message = new Message(sr, remetenteId, resultado.texto(), resultado.mascarado());
        messageRepository.save(message);
        return toDto(message);
    }

    @Transactional(readOnly = true)
    public List<MessageDto> listar(UUID srId, UUID userId) {
        requestRepository.findById(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (!requestRepository.isParticipante(srId, userId)) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        return messageRepository.findByServiceRequestIdOrderByCreatedAtAsc(srId)
                .stream().map(this::toDto).toList();
    }

    private MessageDto toDto(Message m) {
        String nome = userRepository.findById(m.getRemetenteId())
                .map(User::getNome)
                .orElse("Usuário");
        return new MessageDto(m.getId(), m.getRemetenteId(), nome,
                m.getConteudo(), m.isMascarado(), m.getCreatedAt());
    }
}
