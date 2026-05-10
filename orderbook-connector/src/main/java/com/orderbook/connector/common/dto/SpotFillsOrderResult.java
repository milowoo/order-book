package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotFillsOrderResult implements Serializable {

    private static final long serialVersionUID = 5131455104105754881L;

    // 账户ID
    private String accountId;

    // 交易对
    private String symbol;

    // 订单ID
    private String orderId;

    // 成交ID
    private String fillId;

    // 订单类型
    private String orderType;

    // 订单方向
    private String side;

    // 成交价格
    private String fillPrice;

    // 成交数量
    private String fillQuantity;

    // 已成交总额
    private String fillTotalAmount;

    // 创建时间
    @JsonProperty("cTime")
    private String cTime;

    // 扣除手续费币种
    private String feeCcy;

    // 手续费
    private String fees;

    // taker还是maker
    private String takerMakerFlag;
}