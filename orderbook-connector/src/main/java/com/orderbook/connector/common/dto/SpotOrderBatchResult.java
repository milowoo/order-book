package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SpotOrderBatchResult {
    @JsonProperty("successList")
    List<SpotOrderResult> successList;
    @JsonProperty("failureList")
    List<SpotOrderFailResult> failureList;
}