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
@TableName("fee_config")
public class FeeConfigEntity {
    private Long id;
    private String exchange;
    private String symbol;
    private BigDecimal takerRate;
    private BigDecimal makerRate;
    private String feeCurrency;
    private Long updatedAt;
}
