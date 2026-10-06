package com.vocenocoracao.supermercado_api.cart.controller.converter;

import com.vocenocoracao.supermercado_api.cart.dto.CartItemResponseDTO;
import com.vocenocoracao.supermercado_api.cart.dto.CartResponseDTO;
import com.vocenocoracao.supermercado_api.cart.service.CartDetails;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class CartDetailsToCartResponseDTOConverter implements Converter<CartDetails, CartResponseDTO> {

    @Override
    public CartResponseDTO convert(MappingContext<CartDetails, CartResponseDTO> context) {
        CartDetails details = context.getSource();

        List<CartItemResponseDTO> items = details.items().stream().map(this::toItem).toList();

        int itemCount = items.stream().mapToInt(CartItemResponseDTO::quantity).sum();
        BigDecimal total = items.stream()
                .map(CartItemResponseDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponseDTO(
                details.cart() == null ? null : details.cart().getId(),
                items,
                itemCount,
                total
        );
    }

    private CartItemResponseDTO toItem(CartItem item) {
        Product product = item.getProduct();
        boolean available = product.isActive()
                && product.getCategory().isActive()
                && product.getStock() >= item.getQuantity();

        return new CartItemResponseDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                product.getStock(),
                available
        );
    }
}
