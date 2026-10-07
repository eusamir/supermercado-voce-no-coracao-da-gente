package com.vocenocoracao.supermercado_api.payment.message;

import java.util.UUID;

public record PaymentApprovedMessage(UUID paymentId, UUID orderId) {
}
