package com.orderbook.core.strategy.alpha;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 每个交易对的 Alpha 信号计算配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlphaConfig {

    /** 是否启用订单流不平衡信号。 */
    private boolean orderFlowEnabled = true;

    /** 订单流计算中包含的价格档位数量（深度）。*/
    private int orderFlowDepth = 10;

    /** 订单流信号在复合信号中的权重 */
    private double orderFlowWeight = 0.5;

    /** 是否启用动量信号 */
    private boolean momentumEnabled = true;

    /** 动量计算的回看周期数. */
    private int momentumLookback = 5;

    /** 动量信号在复合信号中的权重. */
    private double momentumWeight = 0.5;

    /**  基于 Alpha 信号对目标持仓的最大调整幅度. */
    private double maxAlphaPositionAdjustment = 0.5;

    /** 机器学习 Alpha 信号在复合信号中的权重. */
    private double mlAlphaWeight = 0.3;

    public static AlphaConfig defaultConfig() {
        return new AlphaConfig();
    }
}
