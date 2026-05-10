package com.orderbook.core.domain;

import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Getter
public class AccountBo {
    private final String exchangeName;
    // assetId => assetBalance
    public final Map<String, BalanceBo> balances = Maps.newConcurrentMap();

    public AccountBo(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public void syncBalance(BalanceBo balance) {
        BalanceBo existAssetBalance = getBalance(balance.getCoin());
        if (balance.getUpdateTime() > existAssetBalance.getUpdateTime()) {
            existAssetBalance.setTotal(balance.getTotal());
            existAssetBalance.setAvailable(balance.getAvailable());
            existAssetBalance.setLocked(balance.getLocked());
            existAssetBalance.setUpdateTime(balance.getUpdateTime());
        }
    }

    public BalanceBo getBalance(String coin) {
        return balances.getOrDefault(coin, new BalanceBo(coin));
    }

    public void setBalance(BalanceBo balance) {
        balances.put(balance.getCoin(), balance);
    }
}