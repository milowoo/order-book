package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SpotOrderFailResult {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("clientOid")
    private String clientOid;
    @JsonProperty("errorMsg")
    private String errorMsg;
    @JsonProperty("errorCode")
    private String errorCode;
}