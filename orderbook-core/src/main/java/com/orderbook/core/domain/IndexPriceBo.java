package com.orderbook.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IndexPriceBo {

    private String symbolId;
    private BigDecimal indexPrice;
    private long timestamp;
}