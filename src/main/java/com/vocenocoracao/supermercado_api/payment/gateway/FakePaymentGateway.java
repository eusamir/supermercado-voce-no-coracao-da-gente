package com.vocenocoracao.supermercado_api.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentGateway implements PaymentGateway {

    private final PaymentGatewayProperties properties;

    public FakePaymentGateway(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentGatewayResult charge(UUID orderId, BigDecimal amount) {
        if (amount.compareTo(properties.maxAmount()) > 0) {
            return PaymentGatewayResult.declined("Pagamento recusado: valor acima do limite de " + properties.maxAmount() + ".");
        }

        return PaymentGatewayResult.approved("TXN-" + UUID.randomUUID());
    }
}
