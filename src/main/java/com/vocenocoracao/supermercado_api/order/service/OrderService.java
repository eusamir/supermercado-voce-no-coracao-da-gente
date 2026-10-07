package com.vocenocoracao.supermercado_api.order.service;

import com.vocenocoracao.supermercado_api.order.repository.OrderSummary;
import com.vocenocoracao.supermercado_api.user.entity.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderDetails checkout(User user);

    Page<OrderSummary> findAll(User user, Pageable pageable);

    OrderDetails findById(User user, UUID orderId);
}
