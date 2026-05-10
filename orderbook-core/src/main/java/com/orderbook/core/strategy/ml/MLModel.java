package com.orderbook.core.strategy.ml;

/**
 机器学习模型推理接口。
 实现类可以使用 XGBoost、随机森林、LightGBM 等算法。
 */
public interface MLModel {

    /**
     根据特征向量预测一个值。
     @param features 输入特征（应当已经过归一化/预处理）
     @return 预测值（具体含义取决于模型训练时的目标）
     */
    double predict(double[] features);

    /** Number of features this model expects. */
    int featureCount();

    /** Model name for logging. */
    String getName();
}
