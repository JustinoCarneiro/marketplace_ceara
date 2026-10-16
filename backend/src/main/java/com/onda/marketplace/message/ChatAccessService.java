package com.onda.marketplace.message;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.EnumSet;
import java.util.UUID;

@Service
class ChatAccessService {
    private final ServiceRequestRepository requests;
    private final UserRepository users;
    ChatAccessService(ServiceRequestRepository requests, UserRepository users) {
        this.requests = requests;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public void requireAccess(UUID requestId, UUID userId) {
        if (!users.existsByIdAndAtivoTrue(userId)) throw new BusinessException("FORBIDDEN", "Conversa indisponível.");
        var request = requests.findById(requestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));
        if (!requests.isParticipante(requestId, userId) || !request.getCliente().isAtivo()) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }
        if (!EnumSet.of(ServiceRequestStatus.ACEITO, ServiceRequestStatus.EM_ANDAMENTO,
                ServiceRequestStatus.CONCLUIDO, ServiceRequestStatus.EM_DISPUTA).contains(request.getStatus())) {
            throw new BusinessException("CHAT_INDISPONIVEL", "O chat só fica disponível a partir do aceite da proposta.");
        }
    }
}
