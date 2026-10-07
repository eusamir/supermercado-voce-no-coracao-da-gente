package com.vocenocoracao.supermercado_api.payment.messaging;

public final class PaymentMessaging {

    public static final String EXCHANGE = "payment.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "payment.dlx";

    public static final String REQUESTED_QUEUE = "payment.requested";
    public static final String APPROVED_QUEUE = "payment.approved";
    public static final String DEAD_LETTER_QUEUE = "payment.dlq";

    public static final String REQUESTED_ROUTING_KEY = "payment.requested";
    public static final String APPROVED_ROUTING_KEY = "payment.approved";
    public static final String DEAD_LETTER_ROUTING_KEY = "payment.dlq";

    private PaymentMessaging() {
    }
}
