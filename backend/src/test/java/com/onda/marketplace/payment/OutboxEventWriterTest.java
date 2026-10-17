package com.onda.marketplace.payment;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEventWriterTest {
    @Test
    void resultadoDaCobranca_preservaConfirmacaoConcorrenteDoWebhook() {
        var events = mock(OutboxEventRepository.class);
        var transactions = mock(TransactionRepository.class);
        var snapshot = new Transaction(UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("0.10"), PaymentMethod.PIX, "stable-key");
        var current = new Transaction(snapshot.getServiceRequestId(), BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("0.10"), PaymentMethod.PIX, "stable-key");
        current.reter();
        snapshot.setGatewayTransactionId("gateway-result");
        var event = new OutboxEvent("transaction", UUID.randomUUID(), "PAYMENT_INITIATED", "{}");
        event.reservar();
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        when(events.findByIdComTrava(event.getId())).thenReturn(Optional.of(event));
        when(transactions.findByIdComTrava(any())).thenReturn(Optional.of(current));
        new OutboxEventWriter(events, transactions).salvarResultadoPagamento(snapshot, event, true);
        assertThat(current.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        assertThat(current.getGatewayTransactionId()).isEqualTo("gateway-result");
        verify(transactions, never()).save(snapshot);
    }
}
