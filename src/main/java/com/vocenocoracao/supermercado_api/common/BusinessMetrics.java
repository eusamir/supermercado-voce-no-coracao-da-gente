package com.vocenocoracao.supermercado_api.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void orderPlaced() {
        Counter.builder("supermercado.orders.placed")
                .description("Pedidos feitos no checkout")
                .register(meterRegistry)
                .increment();
    }

    public void paymentProcessed(boolean approved) {
        Counter.builder("supermercado.payments.processed")
                .description("Pagamentos processados pelo gateway")
                .tag("result", approved ? "approved" : "declined")
                .register(meterRegistry)
                .increment();
    }

    public void orderFulfilled(boolean paid) {
        Counter.builder("supermercado.orders.fulfilled")
                .description("Pedidos concluídos após a tentativa de baixa de estoque")
                .tag("result", paid ? "paid" : "cancelled")
                .register(meterRegistry)
                .increment();
    }

    public void messageRepublished(String type) {
        Counter.builder("supermercado.reconciliation.republished")
                .description("Mensagens reenviadas pelo job de reconciliação")
                .tag("type", type)
                .register(meterRegistry)
                .increment();
    }
}
