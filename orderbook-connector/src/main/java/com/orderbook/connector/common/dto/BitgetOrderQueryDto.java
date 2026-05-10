package com.orderbook.connector.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitgetOrderQueryDto {
    // 订单编号
    private String orderId;

    // 币对
    private String symbol;

    // 查询开始时间 (只允许查询近90天数据) 时间戳
    private Long startTime;

    // 查询结束时间 (startTime和endTime间隔不允许超过90天)
    private Long endTime;

    // 上一次查询的最后一条数据的orderId
    private String idLessThan;

    // 查询条数 默认100，最大100
    private Integer limit = 100;

    // 订单类型 default normal normal 普通单 tpsl 止盈止损单
    private String tpslType;

    // 请求时间 Unix毫秒时间戳格式
    private String requestTime;

    // 有效窗口期 单位毫秒 窗口期不超过60s
    private String receiveWindow;

    private String force;
}