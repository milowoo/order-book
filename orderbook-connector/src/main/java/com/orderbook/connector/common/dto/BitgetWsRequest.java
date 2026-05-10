package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
@SuperBuilder
@Jacksonized
public class BitgetWsRequest {

    @JsonProperty("op")
    private Operation operation;

    @Singular
    @JsonProperty("args")
    private List<BitgetChannel> channels;
}