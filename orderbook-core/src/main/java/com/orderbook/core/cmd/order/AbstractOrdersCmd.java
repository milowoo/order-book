package com.orderbook.core.cmd.order;

import com.orderbook.core.config.SerialExecutorManager;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.common.dto.*;
import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.global.service.GlobalTradeService;
import com.orderbook.connector.interfaces.ConnectorFactory;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.utils.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractOrdersCmd extends BaseCmd {

    @Autowired
    protected ConnectorFactory connectorFactory;

    @Autowired
    protected OpenOrdersStore openOrdersStore;

    // 定义为类成员：单线程执行器，保证异步串行执行
    final SerialExecutorManager serialExecutorManager = new SerialExecutorManager();

    protected void batchKlineOrder(GlobalTradeService tradeService, SpotBatchOrdersDto request, SymbolBo symbolBo) {
        // 构建异步任务
        Runnable task = () -> {
            try {
                tradeService.placeBatchOrder(request);
            } catch (Exception e) {
                log.error("batchKlineOrder exception {}", request, e);
            }
        };

        // 使用 SerialExecutorManager 异步串行执行下单任务
        serialExecutorManager.submit(symbolBo.getSymbolId(), "buy", task);
    }

    protected void klineCancelOrder(GlobalTradeService tradeService, CancelOrderParams params, SymbolBo symbolBo) {
        // 构建异步任务
        Runnable task = () -> {
            try {
                tradeService.cancelOrder(params);
            } catch (Exception e) {
                log.error("klineCancelOrder exception {}, params: {}", e, params);
            }
        };

        // 使用 SerialExecutorManager 异步串行执行下单任务
        serialExecutorManager.submitCancel(symbolBo.getSymbolId(), task);
    }

    // 批量取消订单（第一批同步，其余批次异步串行执行）
    protected void batchCancelOrder(ExchangeCode exchangeCode, SymbolBo symbolBo, List<OrderBo> orderList, Map<Long, OrderBo> orderBoMap) {
        if (orderList.isEmpty()) {
            return;
        }

        List<List<OrderBo>> batches = partitionList(orderList, apolloConfig.getMaxCancelOrderLimit());
        GlobalExchange exchange = (GlobalExchange) connectorFactory.getTradingExchange(
                exchangeCode,
                symbolBo.getApiKey(),
                symbolBo.getSecretKey(),
                symbolBo.getPassword()
        );
        GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();

        for (int i = 0; i < batches.size(); i++) {
            List<OrderBo> batch = batches.get(i);
            int batchIndex = i;

            Runnable task = () -> {
                try {
                    SpotCancelBatchOrderDTO request = new SpotCancelBatchOrderDTO();
                    List<SpotOrderResult> list = batch.stream()
                            .map(order -> {
                                SpotOrderResult result = new SpotOrderResult();
                                result.setOrderId(String.valueOf(order.getOrderId()));
                                return result;
                            })
                            .collect(Collectors.toList());
                    request.setSymbol(symbolBo.getSymbol());
                    request.setOrderList(list);

                    SpotOrderBatchResult result = tradeService.cancelBatchOrder(request);

                    // 清理内存
                    for (SpotOrderResult orderResult : result.getSuccessList()) {
                        orderBoMap.remove(Long.parseLong(orderResult.getOrderId()));
                    }

                    Thread.sleep(apolloConfig.getPlaceSleepTime());
                } catch (Exception e) {
                    log.error("batch {}: batchCancelOrder exception, symbol={}, batch={}", batchIndex, symbolBo.getSymbol(), batch, e);
                }
            };

            // 异步串行执行后续批次（每个 symbol 独立执行器）
            serialExecutorManager.submitCancel(symbolBo.getSymbolId(), task);
        }
    }

    protected void batchCancelOrder(GlobalTradeService tradeService, SymbolBo symbolBo, List<String> orderList) {
        if (orderList.isEmpty()) {
            log.warn("batchCancelOrder: order list is empty");
            return;
        }

        List<List<String>> batches = partitionList(orderList, apolloConfig.getMaxCancelOrderLimit());
        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            int batchIndex = i;

            Runnable task = () -> {
                try {
                    SpotCancelBatchOrderDTO request = new SpotCancelBatchOrderDTO();
                    List<SpotOrderResult> list = batch.stream()
                            .map(orderId -> {
                                SpotOrderResult result = new SpotOrderResult();
                                result.setOrderId(orderId);
                                return result;
                            })
                            .collect(Collectors.toList());
                    request.setSymbol(symbolBo.getSymbol());
                    request.setOrderList(list);

                    SpotOrderBatchResult result = tradeService.cancelBatchOrder(request);

                    if (apolloConfig.isMMLogSwitch(symbolBo.getSymbol())) {
                        log.info("batch {}: batchCancelOrder result: {}", batchIndex, result);
                    }

                    try {
                        Thread.sleep(apolloConfig.getPlaceSleepTime());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // good practice
                        log.warn("batch {}: sleep interrupted.", batchIndex);
                    }
                } catch (Exception e) {
                    log.error("batch {}: batchCancelOrder exception, symbol={}, batch={}", batchIndex, symbolBo.getSymbol(), batch, e);
                }
            };

            // 异步串行执行后续批次（每个 symbol 独立执行器）
            serialExecutorManager.submitCancel(symbolBo.getSymbolId(), task);
        }
    }

    // 将列表拆分为固定大小的子列表
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        int total = list.size();
        for (int i = 0; i < total; i += batchSize) {
            result.add(list.subList(i, Math.min(i + batchSize, total)));
        }
        return result;
    }

    protected List<SpotOrdersV2Req> convertSpotOrders(List<PriceLevel> priceLevelList, String side) {
        List<SpotOrdersV2Req> orderList = new ArrayList<>();
        for (PriceLevel priceLevel : priceLevelList) {
            SpotOrdersV2Req request = new SpotOrdersV2Req();
            request.setClientOid(IdGenerator.snakeFlowId());
            request.setOrderType("limit");
            request.setSide(side);
            request.setForce("GTC");
            request.setPrice(priceLevel.getPrice().toPlainString());
            request.setSize(priceLevel.getQuantity().toPlainString());
            orderList.add(request);
        }
        return orderList;
    }

    protected void batchPlaceOrder(ExchangeCode exchangeCode, SymbolBo symbolBo, String side, List<SpotOrdersV2Req> orderList) {
        if (orderList.isEmpty()) {
            log.warn("batchPlaceOrder: order list is empty, symbol={}", symbolBo.getSymbol());
            return;
        }

        List<List<SpotOrdersV2Req>> batches = partitionList(orderList, apolloConfig.getMaxPlaceOrderLimit());
        GlobalExchange exchange = (GlobalExchange) connectorFactory.getTradingExchange(
                exchangeCode,
                symbolBo.getApiKey(),
                symbolBo.getSecretKey(),
                symbolBo.getPassword()
        );
        GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();

        for (int i = 0; i < batches.size(); i++) {
            List<SpotOrdersV2Req> batch = batches.get(i);
            int batchIndex = i;

            Runnable task = () -> {
                try {
                    SpotBatchOrdersDto subRequest = new SpotBatchOrdersDto();
                    subRequest.setSymbol(symbolBo.getSymbol());
                    subRequest.setChannelApiCode("X-CHANNEL-API-CODE");
                    subRequest.setOrderList(batch);

                    long beginTime = System.currentTimeMillis();
                    tradeService.placeBatchOrder(subRequest);
                    log.info("batch {}: batchPlaceOrder end, use time: {} ms", batchIndex, System.currentTimeMillis() - beginTime);
                    Thread.sleep(apolloConfig.getPlaceSleepTime());
                } catch (Exception e) {
                    log.error("batch {}: batchPlaceOrder failed, symbol={}, batch={}", batchIndex, symbolBo.getSymbol(), batch, e);
                }
            };

            // 按顺序异步执行（不并发）
            serialExecutorManager.submit(symbolBo.getSymbolId(), side, task);
        }
    }

    @PreDestroy
    public void destroy() {
        serialExecutorManager.shutdownAll();
    }

    protected ExchangeCode getExchange() {
        return ExchangeCode.OSL_GLOBAL;
    }
}