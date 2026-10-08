package com.vocenocoracao.supermercado_api.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessMetricsTest {

    private MeterRegistry meterRegistry;
    private BusinessMetrics businessMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        businessMetrics = new BusinessMetrics(meterRegistry);
    }

    private double count(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private double count(String name, String tagKey, String tagValue) {
        return meterRegistry.get(name).tag(tagKey, tagValue).counter().count();
    }

    @Test
    void placedOrdersAccumulateInASingleCounter() {
        businessMetrics.orderPlaced();
        businessMetrics.orderPlaced();
        businessMetrics.orderPlaced();

        assertThat(count("supermercado.orders.placed")).isEqualTo(3);
    }

    @Test
    void approvedAndDeclinedPaymentsAreSeparateSeries() {
        businessMetrics.paymentProcessed(true);
        businessMetrics.paymentProcessed(true);
        businessMetrics.paymentProcessed(false);

        assertThat(count("supermercado.payments.processed", "result", "approved")).isEqualTo(2);
        assertThat(count("supermercado.payments.processed", "result", "declined")).isEqualTo(1);
    }

    @Test
    void paidAndCancelledOrdersAreSeparateSeries() {
        businessMetrics.orderFulfilled(true);
        businessMetrics.orderFulfilled(false);
        businessMetrics.orderFulfilled(false);

        assertThat(count("supermercado.orders.fulfilled", "result", "paid")).isEqualTo(1);
        assertThat(count("supermercado.orders.fulfilled", "result", "cancelled")).isEqualTo(2);
    }

    @Test
    void republishedMessagesAreCountedPerType() {
        businessMetrics.messageRepublished("payment.requested");
        businessMetrics.messageRepublished("payment.requested");
        businessMetrics.messageRepublished("payment.approved");

        assertThat(count("supermercado.reconciliation.republished", "type", "payment.requested")).isEqualTo(2);
        assertThat(count("supermercado.reconciliation.republished", "type", "payment.approved")).isEqualTo(1);
    }

    @Test
    void everyCounterHasADescription() {
        businessMetrics.orderPlaced();
        businessMetrics.paymentProcessed(true);
        businessMetrics.orderFulfilled(true);
        businessMetrics.messageRepublished("payment.requested");

        meterRegistry.getMeters().forEach(meter ->
                assertThat(meter.getId().getDescription()).as(meter.getId().getName()).isNotBlank());
    }
}
