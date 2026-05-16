package com.orderbook.core.strategy.ml;

import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

/**
 * XGBoost 模型包装器，实现 {@link MLModel} 接口。
 * <p>
 * 将 XGBoost4j 的 {@link Booster} 适配到统一的 MLModel 接口，
 * 使其可以作为 AlphaSignal 被回测和实盘策略使用。
 * </p>
 * <p>
 * 序列化格式：{@code xgboost_base64:<base64-encoded-booster-bytes>}
 * 通过 modelDataJson 中的 "xgboost_base64:" 前缀与 RandomForestModel 的 JSON 格式区分。
 * </p>
 */
@Slf4j
public class XGBoostModel implements MLModel {

    private static final String BASE64_PREFIX = "xgboost_base64:";

    private final String name;
    private final int featureCount;
    private final Booster booster;

    public XGBoostModel(String name, int featureCount, Booster booster) {
        this.name = name;
        this.featureCount = featureCount;
        this.booster = booster;
    }

    @Override
    public double predict(double[] features) {
        if (features.length != featureCount) {
            log.warn("[XGBoost] Expected {} features but got {}", featureCount, features.length);
            return 0.0;
        }
        try {
            float[] flatData = new float[features.length];
            for (int i = 0; i < features.length; i++) {
                flatData[i] = (float) features[i];
            }
            DMatrix dmat = new DMatrix(flatData, 1, features.length, Float.NaN);
            float[][] preds = booster.predict(dmat);
            return preds.length > 0 ? preds[0][0] : 0.0;
        } catch (Exception e) {
            log.warn("[XGBoost] Prediction failed: {}", e.getMessage());
            return 0.0;
        }
    }

    @Override
    public int featureCount() {
        return featureCount;
    }

    @Override
    public String getName() {
        return name;
    }

    /** 获取底层 XGBoost Booster 实例。 */
    public Booster getBooster() {
        return booster;
    }

    // ---- Serialization ----

    /**
     * 将模型序列化为字符串。
     * 格式：{@code xgboost_base64:<base64-encoded-booster-bytes>}
     */
    public String toJsonString() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            booster.saveModel(bos);
            byte[] modelBytes = bos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(modelBytes);
            return BASE64_PREFIX + base64;
        } catch (Exception e) {
            log.warn("[XGBoost] Serialization failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从字符串反序列化 XGBoost 模型。
     *
     * @param json 序列化字符串（含 {@code xgboost_base64:} 前缀）
     * @return XGBoostModel 实例，或反序列化失败时返回 null
     */
    public static XGBoostModel fromJson(String json) {
        try {
            if (json == null || !json.startsWith(BASE64_PREFIX)) {
                log.warn("[XGBoost] Invalid serialized format, expected '{}' prefix", BASE64_PREFIX);
                return null;
            }
            String base64 = json.substring(BASE64_PREFIX.length());
            byte[] modelBytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bis = new ByteArrayInputStream(modelBytes);
            Booster booster = XGBoost.loadModel(bis);
            // Use a placeholder; the real name/featureCount should come from metadata
            return new XGBoostModel("xgboost", 0, booster);
        } catch (Exception e) {
            log.warn("[XGBoost] Deserialization failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从序列化字符串恢复模型，并提供元数据覆盖。
     *
     * @param json         序列化字符串
     * @param name         模型名称
     * @param featureCount 特征数量
     * @return XGBoostModel 实例
     */
    public static XGBoostModel fromJson(String json, String name, int featureCount) {
        XGBoostModel model = fromJson(json);
        if (model == null) return null;
        return new XGBoostModel(name, featureCount, model.booster);
    }

    /**
     * 从 XGBoost 原生 .model 文件加载。
     *
     * @param file        模型文件
     * @param name        模型名称
     * @param featureCount 特征数量
     * @return XGBoostModel 实例
     */
    public static XGBoostModel loadModelFile(File file, String name, int featureCount) {
        try {
            Booster booster = XGBoost.loadModel(file.getAbsolutePath());
            log.info("[XGBoost] Loaded model from {}: {}", file.getAbsolutePath(), name);
            return new XGBoostModel(name, featureCount, booster);
        } catch (Exception e) {
            log.warn("[XGBoost] Failed to load model file {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * 从 Base64 编码的文本文件加载 XGBoost 模型。
     * 文件内容应为 {@code xgboost_base64:<base64>} 格式。
     *
     * @param file 文本文件
     * @return 序列化字符串（可直接传入 fromJson）
     */
    public static String loadBase64FromFile(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            log.warn("[XGBoost] Failed to read base64 file {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }
}
