package com.vocenocoracao.supermercado_api.payment.message;

import java.util.UUID;

public record PaymentRequestedMessage(UUID paymentId, UUID orderId) {
}
