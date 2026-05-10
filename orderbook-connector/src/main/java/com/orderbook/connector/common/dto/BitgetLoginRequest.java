package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.orderbook.connector.common.convert.InstantToTimestampSecondsConverter;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Jacksonized
public class BitgetLoginRequest {

    @JsonProperty("op")
    private Operation operation;

    @Singular
    @JsonProperty("args")
    private List<LoginPayload> payloads;

    @Data
    @Builder
    @Jacksonized
    public static class LoginPayload {

        @JsonProperty("apiKey")
        private String apiKey;

        @JsonProperty("passphrase")
        private String passphrase;

        @JsonProperty("timestamp")
        @JsonSerialize(converter = InstantToTimestampSecondsConverter.class)
        private Instant timestamp;

        @JsonProperty("sign")
        private String signature;
    }
}