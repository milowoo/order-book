package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Action {
    @JsonProperty("snapshot")
    SNAPSHOT,
    @JsonProperty("update")
    UPDATE
}