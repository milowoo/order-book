package com.orderbook.connector.stream.bitget.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;

import java.time.Clock;

@Data
public final class Config {

    public static final String V2_PUBLIC_WS_URL = "wss://ws.bitget.com/v2/ws/public";
    public static final String V2_PRIVATE_WS_URL = "wss://ws.bitget.com/v2/ws/private";
    public static final String V2_DEMO_PUBLIC_WS_URL = "wss://wspap.bitget.com/v2/ws/public";
    public static final String V2_DEMO_PRIVATE_WS_URL = "wss://wspap.bitget.com/v2/ws/private";

    private ObjectMapper objectMapper;
    private Clock clock;

    private static Config instance = new Config();

    private Config() {
        clock = Clock.systemDefaultZone();

        objectMapper = new ObjectMapper();

        // by default read and write timestamps as milliseconds
        objectMapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        objectMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);

        // don't fail on unknown properties
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // don't write nulls
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // enable parsing to Instant
        objectMapper.registerModule(new JavaTimeModule());
    }

    public static Config getInstance() {
        return instance;
    }
}