package com.vocenocoracao.supermercado_api.order.service;

import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import java.util.List;

public record OrderDetails(Order order, List<OrderItem> items, Payment payment) {
}
