package com.orderbook.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inventory_snapshot")
public class InventorySnapshotEntity {
    private Long id;
    private String symbol;
    private String exchange;
    private BigDecimal netPosition;
    private BigDecimal entryPrice;
    private BigDecimal realizedPnl;
    private BigDecimal totalFees;
    private BigDecimal totalVolume;
    private Integer tradeCount;
    private Long snapshotTime;
    private Long updatedAt;
}
