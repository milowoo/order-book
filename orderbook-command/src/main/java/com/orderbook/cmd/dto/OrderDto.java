package com.orderbook.cmd.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderDto {
    private String orderId;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal quantity;

    public OrderDto(String orderId, BigDecimal price, BigDecimal amount, BigDecimal quantity) {
        this.orderId = orderId;
        this.price = price;
        this.amount = amount;
        this.quantity = quantity;
    }
}