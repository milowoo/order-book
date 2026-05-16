package com.orderbook.core.strategy.ml;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.entity.ModelVersionEntity;
import com.orderbook.core.entity.TrainDatasetEntity;
import com.orderbook.core.mapper.ModelVersionMapper;
import com.orderbook.core.mapper.TrainDatasetMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排完整的机器学习训练流水线：
 * 加载数据 → 训练（RF / XGBoost） → 评估 → 保存 → （可选）激活。
 */
@Slf4j
@Service
public class ModelTrainerService {

    private final CartTrainer cartTrainer;
    private final RandomForestTrainer randomForestTrainer;
    private final XGBoostTrainer xgBoostTrainer;
    private final ModelEvaluator modelEvaluator;
    private final TrainDatasetMapper trainDatasetMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final ApolloConfig apolloConfig;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ModelTrainerService(TrainDatasetMapper trainDatasetMapper,
                                ModelVersionMapper modelVersionMapper,
                                ApolloConfig apolloConfig) {
        this.cartTrainer = new CartTrainer();
        this.randomForestTrainer = new RandomForestTrainer(cartTrainer);
        this.xgBoostTrainer = new XGBoostTrainer();
        this.modelEvaluator = new ModelEvaluator();
        this.trainDatasetMapper = trainDatasetMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.apolloConfig = apolloConfig;
    }

    /**
     * 针对指定交易标的（Symbol）的完整训练流水线。
     * 根据 config.modelType 自动选择 RandomForest 或 XGBoost 训练器。
     */
    public ModelVersionEntity train(String symbol, MLConfig config) {
        String modelType = config.getModelType() != null ? config.getModelType() : "random_forest";
        log.info("[ML] Starting {} training for {}: {}", modelType, symbol, config);

        // 1. Load labeled training data
        List<CartTrainer.TrainingExample> examples = loadTrainingData(symbol, apolloConfig.getMLTrainingDataMaxSamples());
        if (examples.size() < 10) {
            log.warn("[ML] Insufficient training data for {}: {} samples (need >= 10)", symbol, examples.size());
            return null;
        }

        // 2. Split into train/test (80/20)
        int splitIdx = (int) (examples.size() * 0.8);
        List<CartTrainer.TrainingExample> trainData = examples.subList(0, splitIdx);
        List<CartTrainer.TrainingExample> testData = examples.subList(splitIdx, examples.size());

        // 3. Train the model
        String modelName = symbol + "_" + modelType + "_" + System.currentTimeMillis();
        MLModel model;
        if ("xgboost".equals(modelType)) {
            model = xgBoostTrainer.train(modelName, trainData, config);
        } else {
            model = randomForestTrainer.train(modelName, trainData, config);
        }

        if (model == null) {
            log.warn("[ML] Training returned null for {}", symbol);
            return null;
        }

        // 4. Evaluate
        ModelEvaluator.EvaluationResult eval = modelEvaluator.evaluate(model, testData);
        if (model instanceof RandomForestModel) {
            log.info("[ML] Training complete for {}: R²={}, MAE={}, RMSE={}, trees={}",
                    symbol,
                    String.format("%.4f", eval.getRSquared()),
                    String.format("%.4f", eval.getMae()),
                    String.format("%.4f", eval.getRmse()),
                    ((RandomForestModel) model).treeCount());
        } else {
            log.info("[ML] Training complete for {}: R²={}, MAE={}, RMSE={}, modelType={}",
                    symbol,
                    String.format("%.4f", eval.getRSquared()),
                    String.format("%.4f", eval.getMae()),
                    String.format("%.4f", eval.getRmse()),
                    modelType);
        }

        // 5. Save to DB
        return saveModel(model, eval, config, symbol);
    }

    private List<CartTrainer.TrainingExample> loadTrainingData(String symbol, int maxSamples) {
        LambdaQueryWrapper<TrainDatasetEntity> query = new LambdaQueryWrapper<TrainDatasetEntity>()
                .eq(TrainDatasetEntity::getSymbol, symbol)
                .isNotNull(TrainDatasetEntity::getLabel)
                .orderByDesc(TrainDatasetEntity::getCapturedAt)
                .last("LIMIT " + maxSamples);

        List<TrainDatasetEntity> entities = trainDatasetMapper.selectList(query);
        List<CartTrainer.TrainingExample> examples = new ArrayList<>(entities.size());

        for (TrainDatasetEntity entity : entities) {
            try {
                double[] features = MAPPER.readValue(entity.getFeaturesJson(), double[].class);
                examples.add(new CartTrainer.TrainingExample(features, entity.getLabel()));
            } catch (Exception e) {
                log.warn("[ML] Failed to parse training example {}", entity.getId(), e);
            }
        }

        return examples;
    }

    private ModelVersionEntity saveModel(MLModel model, ModelEvaluator.EvaluationResult eval,
                                          MLConfig config, String symbol) {
        try {
            String modelDataJson = modelToJson(model);
            String hyperParamsJson = hyperParamsToJson(config);
            Map<String, Object> metricsMap = new LinkedHashMap<>();
            metricsMap.put("r_squared", eval.getRSquared());
            metricsMap.put("mae", eval.getMae());
            metricsMap.put("rmse", eval.getRmse());
            String metricsJson = MAPPER.writeValueAsString(metricsMap);

            long now = System.currentTimeMillis();

            ModelVersionEntity entity = ModelVersionEntity.builder()
                    .symbol(symbol)
                    .modelName(model.getName())
                    .hyperparametersJson(hyperParamsJson)
                    .metricsJson(metricsJson)
                    .modelDataJson(modelDataJson)
                    .active(false)
                    .createdAt(now)
                    .build();

            modelVersionMapper.insert(entity);
            log.info("[ML] Saved model version {} for {}", entity.getId(), symbol);
            return entity;
        } catch (Exception e) {
            log.error("[ML] Failed to save model for {}", symbol, e);
            return null;
        }
    }

    /**
     * 激活某个模型版本（先停用当前版本，再激活新版本）。
     */
    public boolean activateModel(Long modelId) {
        ModelVersionEntity newActive = modelVersionMapper.selectById(modelId);
        if (newActive == null) {
            log.warn("[ML] Model {} not found", modelId);
            return false;
        }

        // Deactivate current active model for this symbol
        ModelVersionEntity currentActive = modelVersionMapper.selectOne(
                new LambdaQueryWrapper<ModelVersionEntity>()
                        .eq(ModelVersionEntity::getSymbol, newActive.getSymbol())
                        .eq(ModelVersionEntity::getActive, true));
        if (currentActive != null) {
            currentActive.setActive(false);
            modelVersionMapper.updateById(currentActive);
        }

        // Activate new model
        newActive.setActive(true);
        newActive.setActivatedAt(System.currentTimeMillis());
        modelVersionMapper.updateById(newActive);

        log.info("[ML] Activated model {} for {}", modelId, newActive.getSymbol());
        return true;
    }

    /**
     * 获取指定交易标的（Symbol）的活跃模型（从数据库加载）。
     */
    public MLModel getActiveModel(String symbol) {
        ModelVersionEntity entity = modelVersionMapper.selectOne(
                new LambdaQueryWrapper<ModelVersionEntity>()
                        .eq(ModelVersionEntity::getSymbol, symbol)
                        .eq(ModelVersionEntity::getActive, true));
        if (entity == null) return null;

        return modelFromJson(entity.getModelDataJson(), entity.getHyperparametersJson(), entity.getModelName());
    }

    /**
     * 自动训练定时任务。
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void autoTrain() {
        if (apolloConfig.getMLAutoTrainIntervalHours() <= 0) return;

        log.info("[ML] Auto-training check...");
    }

    // ---- JSON serialization helpers ----

    private String modelToJson(MLModel model) throws Exception {
        if (model instanceof XGBoostModel) {
            return ((XGBoostModel) model).toJsonString();
        }
        return ((RandomForestModel) model).toJsonString();
    }

    private MLModel modelFromJson(String modelDataJson, String hyperParamsJson, String modelName) {
        try {
            // Determine model type from hyperparameters
            String modelType = "random_forest";
            if (hyperParamsJson != null && !hyperParamsJson.isEmpty()) {
                try {
                    MLConfig config = MAPPER.readValue(hyperParamsJson, MLConfig.class);
                    if (config.getModelType() != null) {
                        modelType = config.getModelType();
                    }
                } catch (Exception e) {
                    log.warn("[ML] Failed to parse hyperParamsJson, defaulting to random_forest", e);
                }
            }

            if ("xgboost".equals(modelType)) {
                // Try to parse featureCount from modelDataJson; fallback to 0
                return XGBoostModel.fromJson(modelDataJson, modelName, 10);
            }

            // XGBoostModel.fromJson might return null if format doesn't match
            // Fall through to RandomForestModel
            if (modelDataJson != null && modelDataJson.startsWith("xgboost_base64:")) {
                return XGBoostModel.fromJson(modelDataJson, modelName, 10);
            }

            return RandomForestModel.fromJson(modelDataJson);
        } catch (Exception e) {
            log.warn("[ML] Failed to deserialize model", e);
            return null;
        }
    }

    private String hyperParamsToJson(MLConfig config) throws Exception {
        return MAPPER.writeValueAsString(config);
    }

    /**
     * 获取用于训练数据收集的特征提取器。
     */
    public CartTrainer getCartTrainer() { return cartTrainer; }
    public RandomForestTrainer getRandomForestTrainer() { return randomForestTrainer; }
    public XGBoostTrainer getXgBoostTrainer() { return xgBoostTrainer; }

    public long getTrainingDataCount(String symbol) {
        LambdaQueryWrapper<TrainDatasetEntity> query = new LambdaQueryWrapper<TrainDatasetEntity>()
                .eq(TrainDatasetEntity::getSymbol, symbol)
                .isNotNull(TrainDatasetEntity::getLabel);
        return trainDatasetMapper.selectCount(query);
    }
}
