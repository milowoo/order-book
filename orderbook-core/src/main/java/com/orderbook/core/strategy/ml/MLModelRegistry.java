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
 * 每个交易标的（Symbol）对应的活跃机器学习模型注册表。
 * 支持在运行时动态切换模型，无需重启服务。
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
     * 获取指定交易标的（Symbol）的活跃模型。
     * 首次访问时从数据库加载，之后则直接从缓存返回。
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
     * 激活某个模型版本并更新缓存。
     */
    public void activateModel(String symbol, Long modelId) {
        activeModels.remove(symbol); // Clear cache — will reload on next getModel()
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
