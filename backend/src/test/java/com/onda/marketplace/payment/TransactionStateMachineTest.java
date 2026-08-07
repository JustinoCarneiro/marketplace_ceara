package com.onda.marketplace.payment;

import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Máquina de estados financeira (CLAUDE.md): PENDENTE → RETIDO → (LIBERADO | REEMBOLSADO).
 * Antes das guardas, {@code liberar()} era um setter cru: dava para ir de PENDENTE direto
 * a LIBERADO e repassar ao prestador dinheiro que nunca foi cobrado do cliente.
 */
@SuppressWarnings("null")
class TransactionStateMachineTest {

    private Transaction nova() {
        return new Transaction(UUID.randomUUID(), BigDecimal.valueOf(200), BigDecimal.valueOf(30),
                BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-" + UUID.randomUUID());
    }

    private Transaction retida() {
        var tx = nova();
        tx.reter();
        return tx;
    }

    // ----- caminhos válidos -----

    @Test
    void pendente_reter_vaiParaRetido() {
        var tx = nova();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.PENDENTE);
        tx.reter();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
    }

    @Test
    void retido_liberar_vaiParaLiberado() {
        var tx = retida();
        tx.liberar();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.LIBERADO);
    }

    @Test
    void retido_reembolsar_vaiParaReembolsado() {
        var tx = retida();
        tx.reembolsar();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.REEMBOLSADO);
    }

    // ----- o bug que motivou as guardas -----

    @Test
    void pendente_liberar_eRejeitado() {
        var tx = nova();

        assertThatThrownBy(tx::liberar)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAYMENT_TRANSITION");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.PENDENTE);
    }

    @Test
    void pendente_reembolsar_eRejeitado() {
        var tx = nova();

        assertThatThrownBy(tx::reembolsar)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAYMENT_TRANSITION");
    }

    // ----- idempotência (webhook reentregue / retry do outbox) -----

    @Test
    void reter_duasVezes_eNoOp() {
        var tx = retida();
        assertThatCode(tx::reter).doesNotThrowAnyException();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
    }

    @Test
    void liberar_duasVezes_eNoOp() {
        var tx = retida();
        tx.liberar();
        assertThatCode(tx::liberar).doesNotThrowAnyException();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.LIBERADO);
    }

    @Test
    void reembolsar_duasVezes_eNoOp() {
        var tx = retida();
        tx.reembolsar();
        assertThatCode(tx::reembolsar).doesNotThrowAnyException();
        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.REEMBOLSADO);
    }

    // ----- não anda para trás depois que o dinheiro se moveu -----

    @Test
    void liberado_naoVoltaParaRetido() {
        var tx = retida();
        tx.liberar();

        assertThatThrownBy(tx::reter)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAYMENT_TRANSITION");
    }

    @Test
    void liberado_naoViraReembolsado() {
        var tx = retida();
        tx.liberar();

        assertThatThrownBy(tx::reembolsar)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAYMENT_TRANSITION");
    }

    @Test
    void reembolsado_naoViraLiberado() {
        var tx = retida();
        tx.reembolsar();

        assertThatThrownBy(tx::liberar)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAYMENT_TRANSITION");
    }
}
