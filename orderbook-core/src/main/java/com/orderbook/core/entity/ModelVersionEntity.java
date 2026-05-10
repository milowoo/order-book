package com.orderbook.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ML model version metadata and serialized model data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_version")
public class ModelVersionEntity {
    private Long id;
    private String symbol;
    private String modelName;
    private String hyperparametersJson;
    private String metricsJson;
    private String modelDataJson;
    private Boolean active;
    private Long createdAt;
    private Long activatedAt;
}
