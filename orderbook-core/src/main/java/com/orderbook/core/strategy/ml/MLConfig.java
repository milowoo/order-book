package com.orderbook.core.strategy.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ML model configuration per symbol.
 * 每个交易对的机器学习模型配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLConfig {

    /** ML model type: "random_forest", "xgboost", or null to disable. */
    private String modelType = null;

    /** 序列化模型文件的路径。 */
    private String modelPath = null;

    /**用于日志记录的模型名称 */
    private String modelName = "default";

    /** 随机森林中的树的数量（如果是从头训练）。 */
    private int rfNumTrees = 50;

    /** 每棵树的最大深度 */
    private int rfMaxDepth = 6;

    /** 每个叶子节点的最小样本数。 */
    private int rfMinSamplesLeaf = 5;

    /** 随机森林的特征采样比例. */
    private double rfFeatureRatio = 0.6;

    // ---- XGBoost hyperparameters ----

    /** XGBoost 训练轮数。 */
    private int xgbNumRound = 100;

    /** XGBoost 树的最大深度。 */
    private int xgbMaxDepth = 6;

    /** XGBoost 学习率（步长）。 */
    private double xgbEta = 0.3;

    /** XGBoost 分裂最小损失减少量（gamma）。 */
    private double xgbGamma = 0.0;

    /** XGBoost 叶子节点最小权重和。 */
    private int xgbMinChildWeight = 1;

    /** XGBoost 行采样比例。 */
    private double xgbSubsample = 1.0;

    /** XGBoost 列采样比例（每棵树）。 */
    private double xgbColsampleBytree = 0.8;

    /** 机器学习 Alpha 信号在复合 Alpha 中的权重. */
    private double alphaWeight = 0.3;

    public boolean isEnabled() {
        return modelType != null && !modelType.isEmpty();
    }
}
