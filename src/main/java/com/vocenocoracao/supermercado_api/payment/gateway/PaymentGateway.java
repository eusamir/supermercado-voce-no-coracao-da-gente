package com.vocenocoracao.supermercado_api.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    PaymentGatewayResult charge(UUID orderId, BigDecimal amount);
}
