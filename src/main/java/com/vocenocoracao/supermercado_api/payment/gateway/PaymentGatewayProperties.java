package com.vocenocoracao.supermercado_api.payment.gateway;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "payment.gateway")
public record PaymentGatewayProperties(@DefaultValue("1000.00") BigDecimal maxAmount) {
}
