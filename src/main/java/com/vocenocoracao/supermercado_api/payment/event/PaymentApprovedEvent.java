package com.vocenocoracao.supermercado_api.payment.event;

import java.util.UUID;

public record PaymentApprovedEvent(UUID paymentId, UUID orderId) {
}
