package com.orderbook.core.sor;

/**
 * 智能订单路由（SOR）的交易所选择策略。
 */
public enum RoutingStrategy {
    /** 选择吃单费率最低的交易所。 */
    LOWEST_FEE,
    /** 选择历史延迟最低的交易所（即速度最快）。 */
    FASTEST,
    /** 选择流动性最深（盘口厚度最好）的交易所。 */
    BEST_LIQUIDITY,
    /** 基于费率、延迟和流动性分数的加权组合策略。 */
    WEIGHTED
}
