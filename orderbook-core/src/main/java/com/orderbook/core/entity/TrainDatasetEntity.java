package com.orderbook.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Training dataset: features + label for ML model training.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("train_dataset")
public class TrainDatasetEntity {
    private Long id;
    private String symbol;
    private String featuresJson;
    private Double label;
    private Long capturedAt;
    private Long createdAt;
}
