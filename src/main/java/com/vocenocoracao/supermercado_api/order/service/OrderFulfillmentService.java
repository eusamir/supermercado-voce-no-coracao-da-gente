package com.vocenocoracao.supermercado_api.order.service;

import java.util.UUID;

public interface OrderFulfillmentService {

    void fulfill(UUID orderId);
}
