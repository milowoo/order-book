package com.orderbook.connector.common.dto;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public abstract class AbstractOrderDto {

    // 用户ID
    private Long userId;

    // 币对
    private String symbol;

    public String getSymbol() {
        if (StringUtils.isNotEmpty(this.symbol)) {
            return this.symbol.toUpperCase();
        }
        return null;
    }
}