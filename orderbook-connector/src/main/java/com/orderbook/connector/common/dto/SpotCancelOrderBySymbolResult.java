package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SpotCancelOrderBySymbolResult {
    @JsonProperty("symbol")
    private String symbol;
}