package com.vocenocoracao.supermercado_api.order.service;

import com.vocenocoracao.supermercado_api.user.entity.User;

public interface OrderService {

    OrderDetails checkout(User user);
}
