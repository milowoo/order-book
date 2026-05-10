package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.*;

@Data
public class SpotCancelBatchOrderDTO {
    @JsonProperty("userId")
    private Long userId = 0L;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("orderList")
    List<SpotOrderResult> orderList;
}