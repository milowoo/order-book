package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.bitget.config.converter.OrderTypeToStringConverter;
import org.knowm.xchange.bitget.config.converter.StringToOrderTypeConverter;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Jacksonized
public class BitgetPlaceOrderDto {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    @JsonDeserialize(converter = StringToOrderTypeConverter.class)
    @JsonSerialize(converter = OrderTypeToStringConverter.class)
    private Order.OrderType orderSide;

    @JsonProperty("orderType")
    private BitgetOrderInfoDto.OrderType orderType;

    @JsonProperty("force")
    private TimeInForce timeInForce;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("size")
    private BigDecimal size;

    @JsonProperty("clientOid")
    private String clientOid;

    @JsonProperty("triggerPrice")
    private BigDecimal triggerPrice;

    @JsonProperty("tpslType")
    private TpSlType tpSlType;

    @JsonProperty("requestTime")
    private Instant requestTime;

    @JsonProperty("receiveWindow")
    private Instant receiveWindow;

    @JsonProperty("stpMode")
    private StpMode stpMode;

    @JsonProperty("presetTakeProfitPrice")
    private BigDecimal presetTakeProfitPrice;

    @JsonProperty("executeTakeProfitPrice")
    private BigDecimal executeTakeProfitPrice;

    @JsonProperty("presetStopLossPrice")
    private BigDecimal presetStopLossPrice;

    @JsonProperty("executeStopLossPrice")
    private BigDecimal executeStopLossPrice;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    public enum TimeInForce implements Order.IOrderFlags {
        // 普通限价单，一直有效直至取消
        @JsonProperty("gtc")
        GOOD_TIL_CANCELLED,

        // 只做 maker 订单
        @JsonProperty("post_only")
        POST_ONLY,

        // 全部成交或立即取消
        @JsonProperty("fok")
        FILL_OR_KILL,

        // 立即成交并取消剩余
        @JsonProperty("ioc")
        IMMEDIATE_OR_CANCEL
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    public enum TpSlType implements Order.IOrderFlags {
        // 普通单（默认值）
        @JsonProperty("normal")
        NORMAL,

        // 止盈止损单
        @JsonProperty("tpsl")
        SPOT_TP_SL
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    public enum StpMode implements Order.IOrderFlags {
        // none: 不设置STP（默认值）
        @JsonProperty("none")
        NONE,

        // cancel_taker: 取消taker单
        @JsonProperty("cancel_taker")
        CANCEL_TAKER,

        // cancel_maker: 取消maker单
        @JsonProperty("cancel_maker")
        CANCEL_MAKER,

        // cancel_both: 两者都取消
        @JsonProperty("cancel_both")
        CANCEL_BOTH
    }
}