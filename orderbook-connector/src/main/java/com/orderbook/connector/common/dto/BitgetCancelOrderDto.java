package com.orderbook.connector.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitgetCancelOrderDto {
    private String symbol;
    private String orderId;
    private String clientOrderId;
    private String tpslType;
}