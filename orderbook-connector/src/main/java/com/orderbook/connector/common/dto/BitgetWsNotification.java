package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@JsonTypeInfo(
        use = Id.NAME,
        include = As.EXISTING_PROPERTY,
        property = "messageType",
        visible = true,
        defaultImpl = BitgetWsNotification.class
)
@JsonSubTypes({
        @Type(value = BitgetEventNotification.class, name = "event"),
        @Type(value = BitgetTickerNotification.class, name = "ticker"),
        @Type(value = BitgetWsOrderBookSnapshotNotification.class, name = "books"),
        @Type(value = BitgetWsOrderBookSnapshotNotification.class, name = "books1"),
        @Type(value = BitgetWsOrderBookSnapshotNotification.class, name = "books5"),
        @Type(value = BitgetWsOrderBookSnapshotNotification.class, name = "books15"),
        @Type(value = BitgetAccountNotification.class, name = "account"),
        @Type(value = BitgetOrderNotification.class, name = "orders"),
        @Type(value = BitgetWsUserTradeNotification.class, name = "fill"),
})
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BitgetWsNotification<T> {

    @JsonProperty("action")
    private Action action;

    @JsonProperty("op")
    private Operation operation;

    @JsonProperty("arg")
    private BitgetChannel channel;

    @Singular
    @JsonProperty("data")
    private List<T> payloadItems;
}