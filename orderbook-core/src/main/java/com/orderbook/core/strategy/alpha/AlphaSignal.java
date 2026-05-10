package com.orderbook.core.strategy.alpha;

import com.orderbook.core.domain.SymbolBo;

/**
 * 这段代码定义了一个非常标准的策略接口。它就像是一个“模具”，规定了所有的预测策略
 * （不管是看订单流的还是看动量的）都必须遵守的规则：输入交易对信息，输出一个标准化的预测分数。
 Alpha 信号接口。
 计算交易对的方向性信号值。
 正值 = 看涨（预期价格上涨）
 负值 = 看跌（预期价格下跌）
 输出已归一化到 [-1, 1] 范围。
 */
public interface AlphaSignal {

    /**
     计算某个交易对的 Alpha 信号。
     @param symbol 交易对标识（例如 "BTCUSDT"）
     @param symbolBo 交易对配置信息
     @return [-1, 1] 范围内的 Alpha 值，>0 代表看涨，<0 代表看跌
     */
    double computeAlpha(String symbol, SymbolBo symbolBo);

    /** 用于日志记录的人类可读的信号名称。 */
    String getName();
}
