package com.onda.marketplace.notification;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FcmPushSenderTest {
    @Test void transientProviderErrorHonorsDeliveryExceptionContractAndAttemptsOtherAdmins() {
        var store=mock(PushStore.class);
        var gateway=mock(PushGateway.class);
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        when(gateway.enabled()).thenReturn(true);
        when(store.adminDevices()).thenReturn(List.of(first,second));
        when(store.adminToken(first)).thenReturn("fixture-first");
        when(store.adminToken(second)).thenReturn("fixture-second");
        when(gateway.send(eq("fixture-first"),any(),any(),any())).thenThrow(new FcmPushGateway.RetryLater(60));
        when(gateway.send(eq("fixture-second"),any(),any(),any())).thenReturn(PushGateway.Result.SENT);
        assertThatThrownBy(()->new FcmPushSender(store,gateway).enviar("SOS",UUID.randomUUID()))
                .isInstanceOf(NotificationDeliveryException.class).hasMessage("Alerta push não entregue; verificar canal administrativo.").hasNoCause();
        verify(gateway).send(eq("fixture-second"),any(),any(),any());
    }
    @Test void configuredGatewayWithoutAdminDeviceDoesNotSatisfySafetyValidator() {
        var store=mock(PushStore.class);var gateway=mock(PushGateway.class);
        when(gateway.enabled()).thenReturn(true);when(store.hasAdminDevice()).thenReturn(false);
        assertThat(new FcmPushSender(store,gateway).ativo()).isFalse();
    }
}
