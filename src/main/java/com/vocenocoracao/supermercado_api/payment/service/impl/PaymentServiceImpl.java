package com.vocenocoracao.supermercado_api.payment.service.impl;

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
import com.vocenocoracao.supermercado_api.payment.service.PaymentService;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final int FAILURE_REASON_MAX_LENGTH = 255;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void process(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado."));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        PaymentGatewayResult result = paymentGateway.charge(orderId, payment.getAmount());

        if (result.approved()) {
            payment.setStatus(PaymentStatus.APPROVED);
            payment.setTransactionId(result.transactionId());
            paymentRepository.save(payment);
            eventPublisher.publishEvent(new PaymentApprovedEvent(payment.getId(), orderId));
            return;
        }

        payment.setStatus(PaymentStatus.DECLINED);
        payment.setFailureReason(truncate(result.failureReason()));
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAYMENT_DECLINED);
        orderRepository.save(order);
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }

        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
