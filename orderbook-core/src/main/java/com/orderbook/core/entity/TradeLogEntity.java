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
@TableName("trade_log")
public class TradeLogEntity {
    private Long id;
    private String tradeId;
    private String symbol;
    private String side;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal fee;
    private String feeCurrency;
    private String exchange;
    private Long tradeTime;
    private Long createdAt;
}
