package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SpotOrderResult {
    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("clientOid")
    private String clientOid;
}