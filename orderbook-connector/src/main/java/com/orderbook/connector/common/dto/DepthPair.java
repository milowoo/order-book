package com.orderbook.connector.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DepthPair {

    // 价格
    private BigDecimal prize;

    // 数量
    private BigDecimal count;

    // 只做maker
    private Integer timeInForce;

    @Override
    public DepthPair clone() {
        DepthPair pair = new DepthPair();
        pair.setPrize(prize);
        pair.setCount(count);
        pair.setTimeInForce(timeInForce);
        return pair;
    }
}