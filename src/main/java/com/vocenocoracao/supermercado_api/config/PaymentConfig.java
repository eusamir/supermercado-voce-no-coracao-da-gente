package com.vocenocoracao.supermercado_api.config;

import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGatewayProperties;
import com.vocenocoracao.supermercado_api.payment.job.PaymentReconciliationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({PaymentGatewayProperties.class, PaymentReconciliationProperties.class})
public class PaymentConfig {
}
