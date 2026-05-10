package com.orderbook.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBo {

    protected long userId;
    protected String clientOrderId;
    protected long accountId;
    protected long orderId;
    protected String symbolId;

    // 订单类型 limit or market
    protected String orderType;
    // 交易方向
    protected String side;
    // 下单价格
    protected BigDecimal price;
    // 下单数量
    protected BigDecimal quantity;
    // 下单价值
    protected BigDecimal amount;
    // 订单状态
    protected String orderStatus;

    // 已成交易数量
    private BigDecimal dealQuantity;
    // 成交均价
    private BigDecimal dealPriceAvg;
    // 已成交易价值
    private BigDecimal dealAmount;
    // 剩余数量
    protected BigDecimal remainingQty;
    // 剩余价值
    protected BigDecimal remainingAmount;
    protected long createTime;

    public boolean isLimitOrder() {
        return ("limit".equalsIgnoreCase(this.orderType) || "limit_maker".equalsIgnoreCase(this.orderType));
    }

    public boolean isBuyOrder() {
        return "BUY".equalsIgnoreCase(side);
    }

    public boolean isSellOrder() {
        return "SELL".equalsIgnoreCase(side);
    }

    public BigDecimal notional() {
        return this.remainingAmount.compareTo(BigDecimal.ZERO) > 0 ?
                this.remainingAmount : this.remainingQty.multiply(this.price);
    }
}