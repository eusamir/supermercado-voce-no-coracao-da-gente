package com.vocenocoracao.supermercado_api.payment.listener;

import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging;
import com.vocenocoracao.supermercado_api.payment.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestedListener {

    private final PaymentService paymentService;

    public PaymentRequestedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = PaymentMessaging.REQUESTED_QUEUE)
    public void onPaymentRequested(PaymentRequestedMessage message) {
        paymentService.process(message.orderId());
    }
}
