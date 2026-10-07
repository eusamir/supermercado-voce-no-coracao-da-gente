package com.vocenocoracao.supermercado_api.payment.messaging;

import com.vocenocoracao.supermercado_api.payment.event.PaymentApprovedEvent;
import com.vocenocoracao.supermercado_api.payment.event.PaymentRequestedEvent;
import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentEventRelay {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventRelay.class);

    private final PaymentMessagePublisher publisher;

    public PaymentEventRelay(PaymentMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRequested(PaymentRequestedEvent event) {
        try {
            publisher.publishPaymentRequested(new PaymentRequestedMessage(event.paymentId(), event.orderId()));
        } catch (RuntimeException exception) {
            log.error("Falha ao publicar payment.requested do pedido {}; a reconciliação vai reenviar", event.orderId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentApproved(PaymentApprovedEvent event) {
        try {
            publisher.publishPaymentApproved(new PaymentApprovedMessage(event.paymentId(), event.orderId()));
        } catch (RuntimeException exception) {
            log.error("Falha ao publicar payment.approved do pedido {}; a reconciliação vai reenviar", event.orderId(), exception);
        }
    }
}
