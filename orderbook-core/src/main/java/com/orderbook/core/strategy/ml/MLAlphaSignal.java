package com.orderbook.core.strategy.ml;

import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.alpha.AlphaSignal;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import lombok.extern.slf4j.Slf4j;

/**
 * 这段注释描述的是一个基于机器学习的预测策略。它不再依赖简单的数学公式（比如均线），
 * 而是让训练好的模型根据复杂的市场特征来给出一个“信心分”。
 由机器学习模型预测驱动的 Alpha 信号。
 从市场数据中提取特征，将其输入到机器学习模型
 （如随机森林、XGBoost 等）中，并将输出结果作为 Alpha 信号。
 模型输出会通过 Sigmoid 函数钳位（归一化）到 [-1, 1]。
 */

/**
 * 这里的逻辑解析：
 * Driven by ML model predictions：即由机器学习模型驱动。
 * 这是最高级的信号类型。前两个信号（动量、订单流）都是基于固定规则的，
 * 而这个信号是基于“经验”的——模型在大量历史数据上训练过，能发现人类难以察觉的复杂规律。
 * Extracts features：即提取特征。
 * 这就是你刚才翻译的那个 FeatureExtractor 发挥作用的地方。
 * 原始数据（价格、挂单）必须先变成特征向量，模型才能看懂。
 * Sigmoid-clamped：即Sigmoid 钳位。
 * 机器学习模型（特别是回归树）输出的数值范围可能是无限的（比如 -5.0 到 +5.0）。
 * 为了符合 Alpha 接口的标准（-1 到 1），这里再次使用了 Sigmoid 函数（如 tanh）把结果压缩到标准范围内。
 * 这样，这个智能模型的预测就能和简单的动量信号完美地加权融合了。
 */
@Slf4j
public class MLAlphaSignal implements AlphaSignal {

    private final FeatureExtractor featureExtractor;
    private final MLModel model;

    public MLAlphaSignal(OrderBookStore orderBookStore,
                         VolatilityTracker volatilityTracker,
                         MLModel model) {
        this.featureExtractor = new FeatureExtractor(orderBookStore, volatilityTracker);
        this.model = model;
    }

    @Override
    public double computeAlpha(String symbol, SymbolBo symbolBo) {
        try {
            double[] features = featureExtractor.extract(symbol, symbolBo);
            if (features == null) return 0.0;

            double prediction = model.predict(features);

            // Clamp to [-1, 1] via tanh
            return Math.tanh(prediction);
        } catch (Exception e) {
            log.warn("[{}] MLAlphaSignal failed: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String getName() {
        return "ml_" + model.getName();
    }

    /** Get the underlying ML model. */
    public MLModel getModel() {
        return model;
    }

    /** Get the feature extractor (for debugging/training). */
    public FeatureExtractor getFeatureExtractor() {
        return featureExtractor;
    }
}
