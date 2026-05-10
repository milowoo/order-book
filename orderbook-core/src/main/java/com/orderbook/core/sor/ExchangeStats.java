package com.orderbook.core.sor;

import com.orderbook.cmd.ExchangeCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 交易所的运行时统计数据，用于智能订单路由（SOR）的决策。
 */
@Data
@Builder
@AllArgsConstructor
public class ExchangeStats {
    private ExchangeCode exchange;
    private double avgLatencyMicros;
    private BigDecimal takerFeeRate;
    private BigDecimal makerFeeRate;
    private double currentSpreadBps;
    private double fillProbabilityProxy;
    private boolean healthy;
    private long lastUpdated;

    public ExchangeStats() {
        this.takerFeeRate = BigDecimal.valueOf(0.001);
        this.makerFeeRate = BigDecimal.valueOf(0.001);
        this.healthy = true;
        this.lastUpdated = System.currentTimeMillis();
    }
}
