package com.orderbook.core.strategy.ml;

import com.orderbook.core.entity.ModelVersionEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for ML model training and management.
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
     */
    @PostMapping("/train")
    public Map<String, Object> train(@RequestParam String symbol,
                                      @RequestParam(defaultValue = "50") int nTrees,
                                      @RequestParam(defaultValue = "6") int maxDepth,
                                      @RequestParam(defaultValue = "5") int minSamplesLeaf,
                                      @RequestParam(defaultValue = "0.6") double featureRatio) {
        MLConfig config = MLConfig.builder()
                .modelType("random_forest")
                .modelName(symbol + "_rf")
                .rfNumTrees(nTrees)
                .rfMaxDepth(maxDepth)
                .rfMinSamplesLeaf(minSamplesLeaf)
                .rfFeatureRatio(featureRatio)
                .build();

        ModelVersionEntity model = modelTrainerService.train(symbol, config);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", model != null);
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
        // This would use a query in the service; simplified for now
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
        RandomForestModel model = mlModelRegistry.getModel(symbol);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("hasActiveModel", model != null);
        result.put("modelName", model != null ? model.getName() : null);
        result.put("treeCount", model != null ? model.treeCount() : 0);
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
}
