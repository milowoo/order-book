package com.orderbook.core.strategy.ml;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.core.entity.ModelVersionEntity;
import com.orderbook.core.mapper.ModelVersionMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个交易标的（Symbol）对应的活跃机器学习模型注册表。
 * 支持 RandomForest 和 XGBoost 两种模型类型。
 * 支持在运行时动态切换模型，无需重启服务。
 */
@Slf4j
@Service
public class MLModelRegistry {

    private final ModelVersionMapper modelVersionMapper;
    private final Map<String, MLModel> activeModels = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MLModelRegistry(ModelVersionMapper modelVersionMapper) {
        this.modelVersionMapper = modelVersionMapper;
    }

    @PostConstruct
    public void init() {
        log.info("[ML] MLModelRegistry initialized (supports random_forest, xgboost)");
    }

    /**
     * 获取指定交易标的（Symbol）的活跃模型。
     * 首次访问时从数据库加载，之后则直接从缓存返回。
     * 自动根据 DB 中的 hyperparametersJson 中的 modelType 字段
     * 分派到 RandomForestModel.fromJson() 或 XGBoostModel.fromJson()。
     */
    public MLModel getModel(String symbol) {
        return activeModels.computeIfAbsent(symbol, s -> {
            ModelVersionEntity entity = modelVersionMapper.selectOne(
                    new LambdaQueryWrapper<ModelVersionEntity>()
                            .eq(ModelVersionEntity::getSymbol, s)
                            .eq(ModelVersionEntity::getActive, true));
            if (entity == null) return null;
            try {
                return deserializeModel(entity);
            } catch (Exception e) {
                log.warn("[ML] Failed to load active model for {}", s, e);
                return null;
            }
        });
    }

    /**
     * 根据 hyperparametersJson 中的 modelType 分派反序列化。
     */
    private MLModel deserializeModel(ModelVersionEntity entity) {
        try {
            String modelType = "random_forest";
            String hyperParamsJson = entity.getHyperparametersJson();
            if (hyperParamsJson != null && !hyperParamsJson.isEmpty()) {
                try {
                    MLConfig config = MAPPER.readValue(hyperParamsJson, MLConfig.class);
                    if (config.getModelType() != null) {
                        modelType = config.getModelType();
                    }
                } catch (Exception e) {
                    log.warn("[ML] Failed to parse hyperparams, defaulting to random_forest", e);
                }
            }

            String modelDataJson = entity.getModelDataJson();
            String modelName = entity.getModelName();

            if ("xgboost".equals(modelType) ||
                    (modelDataJson != null && modelDataJson.startsWith("xgboost_base64:"))) {
                return XGBoostModel.fromJson(modelDataJson, modelName, 10);
            }

            return RandomForestModel.fromJson(modelDataJson);
        } catch (Exception e) {
            log.warn("[ML] Failed to deserialize model", e);
            return null;
        }
    }

    /**
     * 激活某个模型版本并更新缓存。
     */
    public void activateModel(String symbol, Long modelId) {
        activeModels.remove(symbol);
        ModelVersionEntity entity = modelVersionMapper.selectById(modelId);
        if (entity != null) {
            log.info("[ML] Cached model {} for {} cleared, will reload on next access", modelId, symbol);
        }
    }

    /**
     * 重新加载指定交易标的（Symbol）的活跃模型（并清除缓存）。
     */
    public void reloadModel(String symbol) {
        activeModels.remove(symbol);
        log.debug("[ML] Cleared model cache for {}", symbol);
    }
}
