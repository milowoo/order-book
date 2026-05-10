package com.orderbook.core.store;

import com.google.common.collect.Maps;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.AccountBo;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class AccountStore {
    private final static Map<String, AccountBo> accounts = Maps.newConcurrentMap();

    public static AccountBo getAccount(String exchangeName) {
        return accounts.computeIfAbsent(exchangeName, key -> new AccountBo(key));
    }

    public static AccountBo getAccount(ExchangeCode exchangeCode) {
        return getAccount(exchangeCode.name());
    }
}