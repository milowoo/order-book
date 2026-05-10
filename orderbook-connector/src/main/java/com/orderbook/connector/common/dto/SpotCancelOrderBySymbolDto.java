package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SpotCancelOrderBySymbolDto {

    // 用户ID
    @JsonProperty("userId")
    private Long userId = 0L;

    // 交易对
    @JsonProperty("symbol")
    private String symbol;
}