package com.vocenocoracao.supermercado_api.payment.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.vocenocoracao.supermercado_api.payment.event.PaymentApprovedEvent;
import com.vocenocoracao.supermercado_api.payment.event.PaymentRequestedEvent;
import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentMessagingTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private PaymentMessagePublisher publisher;

    private final UUID paymentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void publisherSendsPaymentRequestedToTheRightExchangeAndRoutingKey() {
        PaymentRequestedMessage message = new PaymentRequestedMessage(paymentId, orderId);

        new PaymentMessagePublisher(rabbitTemplate).publishPaymentRequested(message);

        verify(rabbitTemplate).convertAndSend("payment.exchange", "payment.requested", message);
    }

    @Test
    void publisherSendsPaymentApprovedToTheRightExchangeAndRoutingKey() {
        PaymentApprovedMessage message = new PaymentApprovedMessage(paymentId, orderId);

        new PaymentMessagePublisher(rabbitTemplate).publishPaymentApproved(message);

        verify(rabbitTemplate).convertAndSend("payment.exchange", "payment.approved", message);
    }

    @Test
    void relayForwardsThePaymentRequestedEventAsAMessage() {
        new PaymentEventRelay(publisher).onPaymentRequested(new PaymentRequestedEvent(paymentId, orderId));

        verify(publisher).publishPaymentRequested(new PaymentRequestedMessage(paymentId, orderId));
    }

    @Test
    void relayForwardsThePaymentApprovedEventAsAMessage() {
        new PaymentEventRelay(publisher).onPaymentApproved(new PaymentApprovedEvent(paymentId, orderId));

        verify(publisher).publishPaymentApproved(new PaymentApprovedMessage(paymentId, orderId));
    }

    @Test
    void relayDoesNotPropagateABrokerFailureSoTheCommittedWorkIsNotLost() {
        doThrow(new AmqpConnectException(new RuntimeException("broker fora")))
                .when(publisher).publishPaymentRequested(any());
        doThrow(new AmqpConnectException(new RuntimeException("broker fora")))
                .when(publisher).publishPaymentApproved(any());
        PaymentEventRelay relay = new PaymentEventRelay(publisher);

        assertThatCode(() -> relay.onPaymentRequested(new PaymentRequestedEvent(paymentId, orderId)))
                .doesNotThrowAnyException();
        assertThatCode(() -> relay.onPaymentApproved(new PaymentApprovedEvent(paymentId, orderId)))
                .doesNotThrowAnyException();
    }

    @Test
    void messagingNamesAreWiredConsistently() {
        org.assertj.core.api.Assertions.assertThat(PaymentMessaging.REQUESTED_QUEUE)
                .isEqualTo(PaymentMessaging.REQUESTED_ROUTING_KEY);
        org.assertj.core.api.Assertions.assertThat(PaymentMessaging.APPROVED_QUEUE)
                .isEqualTo(PaymentMessaging.APPROVED_ROUTING_KEY);
    }
}
