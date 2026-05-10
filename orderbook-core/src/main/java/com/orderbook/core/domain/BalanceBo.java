package com.orderbook.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BalanceBo {

    // 币种
    private String coin;
    private BigDecimal total;
    private BigDecimal locked;
    private BigDecimal available;
    private long updateTime;

    public BalanceBo(String coin) {
        this.coin = coin;
        this.total = BigDecimal.ZERO;
        this.available = BigDecimal.ZERO;
        this.locked = BigDecimal.ZERO;
    }
}