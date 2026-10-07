package com.vocenocoracao.supermercado_api.payment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessagePublisher;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMessagePublisher publisher;

    private PaymentReconciliationJob job;

    @BeforeEach
    void setUp() {
        job = new PaymentReconciliationJob(
                paymentRepository,
                publisher,
                new PaymentReconciliationProperties(Duration.ofSeconds(60))
        );
    }

    private Payment payment() {
        Order order = new Order();
        order.setId(UUID.randomUUID());

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        return payment;
    }

    @Test
    void republishesPaymentsStuckAsPending() {
        Payment stuck = payment();
        when(paymentRepository.findStalledPending(any(Instant.class), any(Pageable.class))).thenReturn(List.of(stuck));
        when(paymentRepository.findStalledApproved(any(Instant.class), any(Pageable.class))).thenReturn(List.of());

        job.republishStalledMessages();

        verify(publisher).publishPaymentRequested(new PaymentRequestedMessage(stuck.getId(), stuck.getOrder().getId()));
        verify(publisher, never()).publishPaymentApproved(any());
    }

    @Test
    void republishesApprovedPaymentsWhoseOrderWasNeverFulfilled() {
        Payment stuck = payment();
        when(paymentRepository.findStalledPending(any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        when(paymentRepository.findStalledApproved(any(Instant.class), any(Pageable.class))).thenReturn(List.of(stuck));

        job.republishStalledMessages();

        verify(publisher).publishPaymentApproved(new PaymentApprovedMessage(stuck.getId(), stuck.getOrder().getId()));
        verify(publisher, never()).publishPaymentRequested(any());
    }

    @Test
    void usesTheConfiguredStaleThreshold() {
        when(paymentRepository.findStalledPending(any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        when(paymentRepository.findStalledApproved(any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        Instant before = Instant.now();

        job.republishStalledMessages();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(paymentRepository).findStalledPending(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isBetween(before.minusSeconds(61), Instant.now().minusSeconds(59));
    }

    @Test
    void aFailureOnOnePaymentDoesNotStopTheOthers() {
        Payment first = payment();
        Payment second = payment();
        when(paymentRepository.findStalledPending(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(paymentRepository.findStalledApproved(any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        doThrow(new IllegalStateException("broker fora")).when(publisher)
                .publishPaymentRequested(new PaymentRequestedMessage(first.getId(), first.getOrder().getId()));

        job.republishStalledMessages();

        verify(publisher).publishPaymentRequested(new PaymentRequestedMessage(second.getId(), second.getOrder().getId()));
    }
}
