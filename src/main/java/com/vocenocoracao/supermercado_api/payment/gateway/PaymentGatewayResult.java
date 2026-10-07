package com.vocenocoracao.supermercado_api.payment.gateway;

public record PaymentGatewayResult(boolean approved, String transactionId, String failureReason) {

    public static PaymentGatewayResult approved(String transactionId) {
        return new PaymentGatewayResult(true, transactionId, null);
    }

    public static PaymentGatewayResult declined(String failureReason) {
        return new PaymentGatewayResult(false, null, failureReason);
    }
}
