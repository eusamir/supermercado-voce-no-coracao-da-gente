package com.vocenocoracao.supermercado_api.cart.service;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import java.util.List;

public record CartDetails(Cart cart, List<CartItem> items) {
}
