package com.vocenocoracao.supermercado_api.payment.job;

import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessagePublisher;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private static final Pageable BATCH = PageRequest.of(0, 100);

    private final PaymentRepository paymentRepository;
    private final PaymentMessagePublisher publisher;
    private final PaymentReconciliationProperties properties;

    public PaymentReconciliationJob(
            PaymentRepository paymentRepository,
            PaymentMessagePublisher publisher,
            PaymentReconciliationProperties properties
    ) {
        this.paymentRepository = paymentRepository;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms:30000}")
    public void republishStalledMessages() {
        Instant threshold = Instant.now().minus(properties.staleAfter());

        for (Payment payment : paymentRepository.findStalledPending(threshold, BATCH)) {
            try {
                publisher.publishPaymentRequested(new PaymentRequestedMessage(payment.getId(), payment.getOrder().getId()));
                log.warn("payment.requested reenviado para o pedido {}", payment.getOrder().getId());
            } catch (RuntimeException exception) {
                log.error("Falha ao reenviar payment.requested do pedido {}", payment.getOrder().getId(), exception);
            }
        }

        for (Payment payment : paymentRepository.findStalledApproved(threshold, BATCH)) {
            try {
                publisher.publishPaymentApproved(new PaymentApprovedMessage(payment.getId(), payment.getOrder().getId()));
                log.warn("payment.approved reenviado para o pedido {}", payment.getOrder().getId());
            } catch (RuntimeException exception) {
                log.error("Falha ao reenviar payment.approved do pedido {}", payment.getOrder().getId(), exception);
            }
        }
    }
}
