package com.orderbook.core.strategy.ml;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orderbook.core.entity.ModelVersionEntity;
import com.orderbook.core.mapper.ModelVersionMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active ML models per symbol.
 * Enables dynamic model switching at runtime without restart.
 */
@Slf4j
@Service
public class MLModelRegistry {

    private final ModelVersionMapper modelVersionMapper;
    private final Map<String, RandomForestModel> activeModels = new ConcurrentHashMap<>();

    public MLModelRegistry(ModelVersionMapper modelVersionMapper) {
        this.modelVersionMapper = modelVersionMapper;
    }

    @PostConstruct
    public void init() {
        log.info("[ML] MLModelRegistry initialized");
    }

    /**
     * Get the active model for a symbol.
     * Returns from cache or loads from DB on first access.
     */
    public RandomForestModel getModel(String symbol) {
        return activeModels.computeIfAbsent(symbol, s -> {
            ModelVersionEntity entity = modelVersionMapper.selectOne(
                    new LambdaQueryWrapper<ModelVersionEntity>()
                            .eq(ModelVersionEntity::getSymbol, s)
                            .eq(ModelVersionEntity::getActive, true));
            if (entity == null) return null;
            try {
                return RandomForestModel.fromJson(entity.getModelDataJson());
            } catch (Exception e) {
                log.warn("[ML] Failed to load active model for {}", s, e);
                return null;
            }
        });
    }

    /**
     * Activate a model version and update the cache.
     */
    public void activateModel(String symbol, Long modelId) {
        activeModels.remove(symbol); // Clear cache — will reload on next getModel()
        ModelVersionEntity entity = modelVersionMapper.selectById(modelId);
        if (entity != null) {
            log.info("[ML] Cached model {} for {} cleared, will reload on next access", modelId, symbol);
        }
    }

    /**
     * Reload the active model for a symbol (clear cache).
     */
    public void reloadModel(String symbol) {
        activeModels.remove(symbol);
        log.debug("[ML] Cleared model cache for {}", symbol);
    }
}
