package com.onda.marketplace.message;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.servicerequest.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatAccessServiceTest {
    final ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ChatAccessService guard = new ChatAccessService(requests, users);
    final UUID requestId = UUID.randomUUID(), userId = UUID.randomUUID();

    private void request(ServiceRequestStatus status, boolean ownerActive, boolean participant) {
        var request = new ServiceRequest(); request.setStatus(status);
        var ownerId=UUID.randomUUID(); request.setClienteId(ownerId);
        when(users.existsByIdAndAtivoTrue(ownerId)).thenReturn(ownerActive);
        when(users.existsByIdAndAtivoTrue(userId)).thenReturn(true);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(requests.isParticipante(requestId, userId)).thenReturn(participant);
    }
    @Test void suspendedAccountCannotSubscribe() {
        assertThatThrownBy(() -> guard.requireAccess(requestId, userId)).hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        verifyNoInteractions(requests);
    }
    @Test void thirdPartyCannotSubscribe() {
        request(ServiceRequestStatus.ACEITO, true, false);
        assertThatThrownBy(() -> guard.requireAccess(requestId, userId)).hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }
    @Test void deletedOwnerClosesConversationForOtherParticipant() {
        request(ServiceRequestStatus.CONCLUIDO, false, true);
        assertThatThrownBy(() -> guard.requireAccess(requestId, userId)).hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }
    @Test void cancelledOrUnacceptedRequestHasNoLiveChannel() {
        for (var status : List.of(ServiceRequestStatus.CANCELADO, ServiceRequestStatus.PENDENTE, ServiceRequestStatus.PROPOSTO)) {
            request(status, true, true);
            assertThatThrownBy(() -> guard.requireAccess(requestId, userId)).hasFieldOrPropertyWithValue("code", "CHAT_INDISPONIVEL");
        }
    }
    @Test void participantsMayConnectFromAcceptanceThroughCompletionAndDispute() {
        for (var status : List.of(ServiceRequestStatus.ACEITO, ServiceRequestStatus.EM_ANDAMENTO,
                ServiceRequestStatus.CONCLUIDO, ServiceRequestStatus.EM_DISPUTA)) {
            request(status, true, true);
            assertThatCode(() -> guard.requireAccess(requestId, userId)).doesNotThrowAnyException();
        }
    }
}
