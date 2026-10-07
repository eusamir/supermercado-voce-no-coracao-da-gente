package com.vocenocoracao.supermercado_api.order.controller.converter;

import com.vocenocoracao.supermercado_api.order.dto.OrderSummaryResponseDTO;
import com.vocenocoracao.supermercado_api.order.repository.OrderSummary;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class OrderSummaryToOrderSummaryResponseDTOConverter implements Converter<OrderSummary, OrderSummaryResponseDTO> {

    @Override
    public OrderSummaryResponseDTO convert(MappingContext<OrderSummary, OrderSummaryResponseDTO> context) {
        OrderSummary orderSummary = context.getSource();

        return new OrderSummaryResponseDTO(
                orderSummary.id(),
                orderSummary.status(),
                orderSummary.total(),
                orderSummary.createdAt(),
                orderSummary.paymentStatus()
        );
    }
}
