package com.vocenocoracao.supermercado_api.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway =
            new FakePaymentGateway(new PaymentGatewayProperties(new BigDecimal("1000.00")));

    @Test
    void approvesAnAmountBelowTheLimitWithATransactionId() {
        PaymentGatewayResult result = gateway.charge(UUID.randomUUID(), new BigDecimal("49.87"));

        assertThat(result.approved()).isTrue();
        assertThat(result.transactionId()).startsWith("TXN-").hasSizeLessThanOrEqualTo(100);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void approvesExactlyTheLimit() {
        assertThat(gateway.charge(UUID.randomUUID(), new BigDecimal("1000.00")).approved()).isTrue();
    }

    @Test
    void declinesAnAmountAboveTheLimitWithAReason() {
        PaymentGatewayResult result = gateway.charge(UUID.randomUUID(), new BigDecimal("1000.01"));

        assertThat(result.approved()).isFalse();
        assertThat(result.transactionId()).isNull();
        assertThat(result.failureReason()).contains("1000.00");
    }

    @Test
    void generatesADifferentTransactionIdForEachCharge() {
        String first = gateway.charge(UUID.randomUUID(), BigDecimal.TEN).transactionId();
        String second = gateway.charge(UUID.randomUUID(), BigDecimal.TEN).transactionId();

        assertThat(first).isNotEqualTo(second);
    }
}
