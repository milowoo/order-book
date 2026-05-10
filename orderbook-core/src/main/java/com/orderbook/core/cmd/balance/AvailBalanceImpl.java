package com.orderbook.core.cmd.balance;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.balance.AvailBalance;
import com.orderbook.connector.interfaces.ConnectorFactory;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.AccountBo;
import com.orderbook.core.domain.BalanceBo;
import com.orderbook.core.store.AccountStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.AVAIL_BALANCE)
@RequiredArgsConstructor
public class AvailBalanceImpl implements AvailBalance {
    private final ConnectorFactory connectorFactory;

    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String ccy) {
        try {
            AccountBo accountBo = AccountStore.getAccount(exchangeCode.name());
            if (accountBo != null) {
                BalanceBo balanceBo = accountBo.getBalance(ccy);
                if (balanceBo != null) {
                    return balanceBo.getAvailable();
                }
            }

            Exchange exchange = connectorFactory.getExchange(exchangeCode, true);
            Wallet wallet = exchange.getAccountService().getAccountInfo().getWallet();
            Balance balance = wallet.getBalance(Currency.getInstance(ccy));
            return balance.getAvailable();
        } catch (Exception e) {
            log.error(String.format("get exchange:%s %s balance error", exchangeCode, ccy), e);
            return BigDecimal.ZERO;
        }
    }
}