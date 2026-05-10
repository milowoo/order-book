package com.orderbook.core.cmd.openorder;

import com.google.common.collect.Lists;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.OpenOrdersBo;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.store.OpenOrdersStore;

import java.util.List;
import java.util.Map;

public abstract class AbstractOpenOrdersCmd extends BaseCmd {

    /**
     * 获取某交易所下symbol所有的订单
     * Params:
     *   env
     *   exchangeCode
     *   symbolId
     * Returns:
     */
    protected List<OrderBo> getAllBotOpenOrders(Map<String, Object> env, ExchangeCode exchangeCode, String symbolId) {
        OpenOrdersBo openOrdersBo = OpenOrdersStore.getOpenOrders(exchangeCode, symbolId);
        List<OrderBo> result = Lists.newArrayList();
        result.addAll(openOrdersBo.getAllOrder());
        return result;
    }
}