package com.vocenocoracao.supermercado_api.payment.listener;

import com.vocenocoracao.supermercado_api.order.service.OrderFulfillmentService;
import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentApprovedListener {

    private final OrderFulfillmentService orderFulfillmentService;

    public PaymentApprovedListener(OrderFulfillmentService orderFulfillmentService) {
        this.orderFulfillmentService = orderFulfillmentService;
    }

    @RabbitListener(queues = PaymentMessaging.APPROVED_QUEUE)
    public void onPaymentApproved(PaymentApprovedMessage message) {
        orderFulfillmentService.fulfill(message.orderId());
    }
}
