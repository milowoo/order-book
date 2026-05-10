package com.orderbook.connector.common.dto;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotModifyDepthParam implements Serializable {

    // 用户ID
    private Long userId;

    // 币对
    private String symbolId;

    private Integer businessLine;

    private String secondBusinessLine;

    // 客户端请求时间
    private long requestTime;

    // 客户端标示
    private long cycleId;

    // 卖的正容忍值 当 new-old>0时候 绝对值低于这个不变动
    private BigDecimal askTolerateValue;

    // 卖的负容忍值 当 new-old<0时候 绝对值低于这个不变动
    private BigDecimal askNegativeTolerateValue;

    // 买的正容忍值 当 new-old>0时候 绝对值低于这个不变动
    private BigDecimal bidTolerateValue;

    // 买的负容忍值 当 new-old<0时候 绝对值低于这个不变动
    private BigDecimal bidNegativeTolerateValue;

    // 卖 - 深度信息
    private String asks;

    // 买 - 深度信息
    private String bids;

    private Boolean force = true;
}