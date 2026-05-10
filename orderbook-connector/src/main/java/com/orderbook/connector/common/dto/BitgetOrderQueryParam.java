package com.orderbook.connector.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitgetOrderQueryParam implements OpenOrdersParams {

    // 订单编号
    private String orderId;

    // 币对
    private CurrencyPair currencyPair;

    // 查询开始时间 (只允许查询近90天数据) 时间戳
    private String startTime;

    // 查询结束时间 (startTime和endTime间隔不允许超过90天)
    private String endTime;

    // 上一次查询的最后一条数据的orderId
    private String idLessThan;

    // 查询条数 默认100，最大100
    private String limit = "500";

    // 订单类型 default normal normal 普通单 tpsl 止盈止损单
    private String tpslType;

    // 请求时间 Unix毫秒时间戳格式
    private String requestTime;

    // 有效窗口期 单位毫秒 窗口期不超过60s
    private String receiveWindow;

    /**
     * Checks if passed order is suitable for open orders params. May be used for XChange side orders filtering
     * @param order - The order to filter.
     * @return: true if order is ok
     */
    @Override
    public boolean accept(LimitOrder order) {
        return false;
    }

    /**
     * Added later, this method allows the filter to also apply to stop orders, at a small cost. It should be
     * explicitly implemented for better performance.
     * @param order - The order to filter.
     * @return: true if order is ok.
     */
    @Override
    public boolean accept(Order order) {
        return OpenOrdersParams.super.accept(order);
    }

    public BitgetOrderQueryParam(CurrencyPair currencyPair) {
        this.currencyPair = currencyPair;
    }
}