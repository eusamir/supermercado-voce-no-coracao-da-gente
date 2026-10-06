package com.vocenocoracao.supermercado_api.cart.service;

import com.vocenocoracao.supermercado_api.user.entity.User;
import java.util.UUID;

public interface CartService {

    CartDetails getCart(User user);

    CartDetails addItem(User user, UUID productId, int quantity);

    CartDetails updateItem(User user, UUID productId, int quantity);

    CartDetails removeItem(User user, UUID productId);
}
