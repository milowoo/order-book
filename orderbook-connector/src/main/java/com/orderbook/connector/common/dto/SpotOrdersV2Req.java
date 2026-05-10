package com.orderbook.connector.common.dto;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotOrdersV2Req implements Serializable {

    // 订单方向
    private String side;

    // 订单类型
    private String orderType;

    // 订单控制类型
    private String force;

    // 委托价格，仅适用于限价单
    private String price;

    // 数量
    private String size;

    // 客户端订单ID
    private String clientOid;
}