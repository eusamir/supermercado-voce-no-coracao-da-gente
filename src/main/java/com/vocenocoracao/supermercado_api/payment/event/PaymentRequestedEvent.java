package com.vocenocoracao.supermercado_api.payment.event;

import java.util.UUID;

public record PaymentRequestedEvent(UUID paymentId, UUID orderId) {
}
