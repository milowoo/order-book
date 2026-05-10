package com.orderbook.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PriceLevel implements Comparable<PriceLevel> {

    private BigDecimal price;
    private BigDecimal quantity;
    private boolean updated;

    public PriceLevel(BigDecimal price, BigDecimal quantity) {
        this.updated = true;
        this.price = price;
        this.quantity = quantity;
    }

    public void adjust(BigDecimal delta) {
        this.quantity = this.quantity.add(delta);
        this.updated = true;
    }

    public boolean isEmpty() {
        return this.quantity.compareTo(BigDecimal.ZERO) <= 0;
    }

    public BigDecimal notional() {
        return this.quantity.multiply(this.price);
    }

    @Override
    public int compareTo(PriceLevel o) {
        return price.compareTo(o.price);
    }
}