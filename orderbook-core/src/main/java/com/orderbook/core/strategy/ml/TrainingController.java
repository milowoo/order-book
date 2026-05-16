package com.orderbook.core.strategy.ml;

import com.orderbook.core.entity.ModelVersionEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于机器学习模型训练与管理的 REST API。
 * 支持 RandomForest 和 XGBoost 两种模型类型。
 */
@RestController
@RequestMapping("/api/ml")
public class TrainingController {

    private final ModelTrainerService modelTrainerService;
    private final MLModelRegistry mlModelRegistry;

    public TrainingController(ModelTrainerService modelTrainerService,
                               MLModelRegistry mlModelRegistry) {
        this.modelTrainerService = modelTrainerService;
        this.mlModelRegistry = mlModelRegistry;
    }

    /**
     * Trigger training for a symbol.
     * <p>
     * 支持两种模型类型：
     * <ul>
     *   <li>{@code random_forest}（默认）：使用 nTrees / maxDepth / minSamplesLeaf / featureRatio</li>
     *   <li>{@code xgboost}：使用 numRound / eta / gamma / minChildWeight / subsample / colsampleBytree</li>
     * </ul>
     */
    @PostMapping("/train")
    public Map<String, Object> train(@RequestParam String symbol,
                                      @RequestParam(defaultValue = "random_forest") String modelType,
                                      // RandomForest params
                                      @RequestParam(defaultValue = "50") int nTrees,
                                      @RequestParam(defaultValue = "6") int maxDepth,
                                      @RequestParam(defaultValue = "5") int minSamplesLeaf,
                                      @RequestParam(defaultValue = "0.6") double featureRatio,
                                      // XGBoost params
                                      @RequestParam(defaultValue = "100") int numRound,
                                      @RequestParam(defaultValue = "0.3") double eta,
                                      @RequestParam(defaultValue = "0.0") double gamma,
                                      @RequestParam(defaultValue = "1") int minChildWeight,
                                      @RequestParam(defaultValue = "1.0") double subsample,
                                      @RequestParam(defaultValue = "0.8") double colsampleBytree) {
        MLConfig.MLConfigBuilder builder = MLConfig.builder()
                .modelType(modelType)
                .modelName(symbol + "_" + modelType);

        if ("xgboost".equals(modelType)) {
            builder
                    .xgbNumRound(numRound)
                    .xgbMaxDepth(maxDepth)    // reuse maxDepth param for XGBoost
                    .xgbEta(eta)
                    .xgbGamma(gamma)
                    .xgbMinChildWeight(minChildWeight)
                    .xgbSubsample(subsample)
                    .xgbColsampleBytree(colsampleBytree);
        } else {
            builder
                    .rfNumTrees(nTrees)
                    .rfMaxDepth(maxDepth)
                    .rfMinSamplesLeaf(minSamplesLeaf)
                    .rfFeatureRatio(featureRatio);
        }

        ModelVersionEntity model = modelTrainerService.train(symbol, builder.build());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", model != null);
        result.put("modelType", modelType);
        if (model != null) {
            result.put("modelId", model.getId());
            result.put("modelName", model.getModelName());
            result.put("symbol", symbol);
        }
        return result;
    }

    /**
     * List saved models for a symbol.
     */
    @GetMapping("/models")
    public List<ModelVersionEntity> listModels(@RequestParam String symbol) {
        return List.of();
    }

    /**
     * Activate a saved model version.
     */
    @PostMapping("/activate/{modelId}")
    public Map<String, Object> activate(@PathVariable Long modelId) {
        boolean success = modelTrainerService.activateModel(modelId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        return result;
    }

    /**
     * Get the currently active model info for a symbol.
     */
    @GetMapping("/active/{symbol}")
    public Map<String, Object> getActiveModel(@PathVariable String symbol) {
        MLModel model = mlModelRegistry.getModel(symbol);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("hasActiveModel", model != null);
        result.put("modelName", model != null ? model.getName() : null);
        result.put("modelType", model != null ? detectModelType(model) : null);
        if (model instanceof RandomForestModel) {
            result.put("treeCount", ((RandomForestModel) model).treeCount());
        }
        return result;
    }

    /**
     * Get training data stats for a symbol.
     */
    @GetMapping("/training-data/{symbol}")
    public Map<String, Object> getTrainingDataStats(@PathVariable String symbol) {
        long count = modelTrainerService.getTrainingDataCount(symbol);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("labeledCount", count);
        return result;
    }

    private String detectModelType(MLModel model) {
        if (model instanceof XGBoostModel) return "xgboost";
        if (model instanceof RandomForestModel) return "random_forest";
        return "unknown";
    }
}
