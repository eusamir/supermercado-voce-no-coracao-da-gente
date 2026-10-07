package com.vocenocoracao.supermercado_api.payment.messaging;

import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentRequested(PaymentRequestedMessage message) {
        rabbitTemplate.convertAndSend(PaymentMessaging.EXCHANGE, PaymentMessaging.REQUESTED_ROUTING_KEY, message);
    }

    public void publishPaymentApproved(PaymentApprovedMessage message) {
        rabbitTemplate.convertAndSend(PaymentMessaging.EXCHANGE, PaymentMessaging.APPROVED_ROUTING_KEY, message);
    }
}
