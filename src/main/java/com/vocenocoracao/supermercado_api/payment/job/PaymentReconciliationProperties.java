package com.vocenocoracao.supermercado_api.payment.job;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "payment.reconciliation")
public record PaymentReconciliationProperties(@DefaultValue("60s") Duration staleAfter) {
}
