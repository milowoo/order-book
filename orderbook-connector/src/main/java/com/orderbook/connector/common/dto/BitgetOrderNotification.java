package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.bitget.config.converter.OrderTypeToStringConverter;
import org.knowm.xchange.bitget.config.converter.StringToOrderTypeConverter;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.dto.Order;

import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BitgetOrderNotification extends BitgetWsNotification<BitgetOrderNotification.OrderData> {

    @Data
    @Builder
    @Jacksonized
    public static class OrderData {
        // 交易对
        @JsonProperty("instId")
        private String instrument;

        // 订单ID
        @JsonProperty("orderId")
        private String orderId;

        // 自定义订单id
        @JsonProperty("clientOid")
        private String clientOid;

        // 委托价格
        @JsonProperty("price")
        private String price;

        // 委托数量
        @JsonProperty("size")
        private String size;

        // - 当orderType=limit时，newSize表示base coin数量;
        @JsonProperty("newSize")
        private String newSize;

        // 买入金额，市价买入时返回
        @JsonProperty("notional")
        private String notional;

        // 订单类型，market: 市价单 limit: 限价单
        @JsonProperty("ordType")
        private String ordType;

        // 订单有效期，gtc普通限价单，一直有效直至取消；post_only只做maker订单；fok全部成交或立即取消；ioc立即成交并取消剩余
        @JsonProperty("force")
        private String force;

        // 订单方向
        @JsonProperty("side")
        @JsonDeserialize(converter = StringToOrderTypeConverter.class)
        @JsonSerialize(converter = OrderTypeToStringConverter.class)
        private Order.OrderType side;

        // 最新成交价格
        @JsonProperty("fillPrice")
        private String fillPrice;

        // 最新成交ID
        @JsonProperty("tradeId")
        private String tradeId;

        // 最新成交数量
        @JsonProperty("baseVolume")
        private String baseVolume;

        // 最新成交时间 Unix毫秒时间戳，例如1690196141868
        @JsonProperty("fillTime")
        private String fillTime;

        // 最新数量
        @JsonProperty("fillFee")
        private String fillFee;

        // 最新成交币种
        @JsonProperty("fillFeeCoin")
        private String fillFeeCoin;

        // 最新一笔成交的流动性方向
        @JsonProperty("tradeScope")
        private String tradeScope;

        // 累计已成交数量
        @JsonProperty("accBaseVolume")
        private String accBaseVolume;

        // 累计成交均价，如果成交数量为0，该字段也为0
        @JsonProperty("priceAvg")
        private String priceAvg;

        // 订单状态
        @JsonProperty("status")
        private BitgetOrderInfoDto.BitgetOrderStatus status;

        // 订单来源
        @JsonProperty("enterPointSource")
        private String enterPointSource;

        // 手续费list
        @JsonProperty("feeDetail")
        private List feeDetail;

        // 订单创建时间，Unix时间戳的毫秒数格式，如 1630410492847
        @JsonProperty("cTime")
        private String cTime;

        // 订单更新时间，Unix时间戳的毫秒数格式，如 1597026383085
        @JsonProperty("uTime")
        private String uTime;

        // STP模式
        @JsonProperty("stpMode")
        private String stpMode;
    }
}