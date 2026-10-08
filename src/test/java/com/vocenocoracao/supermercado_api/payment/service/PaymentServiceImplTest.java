package com.vocenocoracao.supermercado_api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.common.BusinessMetrics;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.event.PaymentApprovedEvent;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGateway;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGatewayResult;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.payment.service.impl.PaymentServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private PaymentServiceImpl service;

    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("49.87"));
    }

    private void stubLoading() {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void approvedChargeMarksThePaymentAndPublishesTheApprovedEvent() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.approved("TXN-1"));

        service.process(order.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo("TXN-1");
        assertThat(payment.getFailureReason()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(paymentRepository).save(payment);
        verify(eventPublisher).publishEvent(new PaymentApprovedEvent(payment.getId(), order.getId()));
    }

    @Test
    void declinedChargeMarksThePaymentAndTheOrderWithoutPublishingAnything() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("Pagamento recusado: valor acima do limite."));

        service.process(order.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getFailureReason()).isEqualTo("Pagamento recusado: valor acima do limite.");
        assertThat(payment.getTransactionId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        verify(orderRepository).save(order);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void declinedReasonLongerThanTheColumnIsTruncated() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("x".repeat(400)));

        service.process(order.getId());

        assertThat(payment.getFailureReason()).hasSize(255);
    }

    @Test
    void anAlreadyProcessedPaymentIsIgnoredWithoutChargingAgain() {
        stubLoading();
        payment.setStatus(PaymentStatus.APPROVED);

        service.process(order.getId());

        verify(paymentGateway, never()).charge(any(), any());
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aDeclinedPaymentIsNotChargedAgainOnRedelivery() {
        stubLoading();
        payment.setStatus(PaymentStatus.DECLINED);
        order.setStatus(OrderStatus.PAYMENT_DECLINED);

        service.process(order.getId());

        verify(paymentGateway, never()).charge(any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
    }

    @Test
    void unknownOrderIsReportedSoTheMessageCanGoToTheDeadLetterQueue() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderWithoutPaymentIsReported() {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(order.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anApprovedChargeIsCountedAsApproved() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.approved("TXN-1"));

        service.process(order.getId());

        verify(businessMetrics).paymentProcessed(true);
    }

    @Test
    void aDeclinedChargeIsCountedAsDeclined() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("recusado"));

        service.process(order.getId());

        verify(businessMetrics).paymentProcessed(false);
    }

    @Test
    void anAlreadyProcessedPaymentIsNotCountedAgain() {
        stubLoading();
        payment.setStatus(PaymentStatus.APPROVED);

        service.process(order.getId());

        verify(businessMetrics, never()).paymentProcessed(anyBoolean());
    }
}
