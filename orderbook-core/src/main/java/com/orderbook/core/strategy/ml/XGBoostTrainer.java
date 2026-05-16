package com.orderbook.core.strategy.ml;

import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XGBoost 训练器。
 * <p>
 * 使用 {@link CartTrainer.TrainingExample} 列表作为输入，
 * 通过 XGBoost4j 训练梯度提升树模型。
 * 与 {@link XGBoostModel} 配合使用，支持完整的训练→保存→加载流水线。
 * </p>
 */
@Slf4j
public class XGBoostTrainer {

    /**
     * 训练 XGBoost 回归模型。
     *
     * @param name   模型名称
     * @param data   训练数据
     * @param config ML 配置（包含 XGBoost 超参数）
     * @return 训练好的 XGBoostModel，或训练失败时返回 null
     */
    public XGBoostModel train(String name, List<CartTrainer.TrainingExample> data, MLConfig config) {
        if (data == null || data.size() < 5) {
            log.warn("[XGBoostTrainer] Insufficient training data: {}", data != null ? data.size() : 0);
            return null;
        }

        int featureCount = data.get(0).features.length;
        int nRows = data.size();

        try {
            // Convert to float[][] for DMatrix
            float[] flatData = new float[nRows * featureCount];
            float[] labels = new float[nRows];

            for (int i = 0; i < nRows; i++) {
                CartTrainer.TrainingExample ex = data.get(i);
                System.arraycopy(
                        toFloatArray(ex.features), 0,
                        flatData, i * featureCount, featureCount);
                labels[i] = (float) ex.label;
            }

            DMatrix dmat = new DMatrix(flatData, nRows, featureCount, Float.NaN);
            dmat.setLabel(labels);

            // Build training params
            Map<String, Object> params = new HashMap<>();
            params.put("objective", "reg:squarederror");
            params.put("max_depth", config.getXgbMaxDepth());
            params.put("eta", config.getXgbEta());
            params.put("gamma", config.getXgbGamma());
            params.put("min_child_weight", config.getXgbMinChildWeight());
            params.put("subsample", config.getXgbSubsample());
            params.put("colsample_bytree", config.getXgbColsampleBytree());
            // Suppress stdout output from XGBoost
            params.put("silent", 1);

            int numRound = config.getXgbNumRound();

            log.info("[XGBoostTrainer] Training {}: {} rows, {} features, {} rounds, max_depth={}, eta={}",
                    name, nRows, featureCount, numRound,
                    config.getXgbMaxDepth(), config.getXgbEta());

            Booster booster = XGBoost.train(dmat, params, numRound, new HashMap<>(), null, null);

            log.info("[XGBoostTrainer] Training complete for {}", name);
            return new XGBoostModel(name, featureCount, booster);

        } catch (Exception e) {
            log.error("[XGBoostTrainer] Training failed for {}: {}", name, e.getMessage(), e);
            return null;
        }
    }

    private static float[] toFloatArray(double[] arr) {
        float[] result = new float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = (float) arr[i];
        }
        return result;
    }
}
