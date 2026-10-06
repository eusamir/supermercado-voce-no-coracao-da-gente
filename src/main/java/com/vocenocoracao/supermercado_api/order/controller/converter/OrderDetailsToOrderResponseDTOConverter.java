package com.vocenocoracao.supermercado_api.order.controller.converter;

import com.vocenocoracao.supermercado_api.order.dto.OrderItemResponseDTO;
import com.vocenocoracao.supermercado_api.order.dto.OrderPaymentResponseDTO;
import com.vocenocoracao.supermercado_api.order.dto.OrderResponseDTO;
import com.vocenocoracao.supermercado_api.order.service.OrderDetails;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import java.util.List;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class OrderDetailsToOrderResponseDTOConverter implements Converter<OrderDetails, OrderResponseDTO> {

    @Override
    public OrderResponseDTO convert(MappingContext<OrderDetails, OrderResponseDTO> context) {
        OrderDetails details = context.getSource();

        List<OrderItemResponseDTO> items = details.items().stream().map(this::toItem).toList();

        return new OrderResponseDTO(
                details.order().getId(),
                details.order().getStatus(),
                details.order().getTotal(),
                details.order().getCreatedAt(),
                items,
                new OrderPaymentResponseDTO(details.payment().getStatus(), details.payment().getAmount())
        );
    }

    private OrderItemResponseDTO toItem(OrderItem item) {
        return new OrderItemResponseDTO(
                item.getProduct().getId(),
                item.getName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
