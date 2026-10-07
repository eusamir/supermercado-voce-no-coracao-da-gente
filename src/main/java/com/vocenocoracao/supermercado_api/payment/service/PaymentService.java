package com.vocenocoracao.supermercado_api.payment.service;

import java.util.UUID;

public interface PaymentService {

    void process(UUID orderId);
}
