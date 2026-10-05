package com.vocenocoracao.supermercado_api.order.entity;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_DECLINED,
    PAID,
    CANCELLED;
}
