package com.orderbook.core.exchange.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.lmax.disruptor.EventHandler;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

@Slf4j
public abstract class AbstractSymbolOrderBooks implements EventHandler<OrderBook> {
    private final ConcurrentSkipListMap<BigDecimal, BigDecimal> ask = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<BigDecimal, BigDecimal> bid = new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    public abstract String symbol();

    public abstract ExchangeCode platform();

    @Override
    public void onEvent(OrderBook event, long sequence, boolean endOfBatch) throws Exception {
        //识别平台和币对
        if (!(symbol().equals(event.getSymbol()) && platform().equals(event.getExchange()))) {
            return;
        }

        if (Objects.isNull(event) || (CollUtil.isEmpty(event.getAsk()) && CollUtil.isEmpty(event.getBid()))) {
            return;
        }
        insert(event);
        //checkSum 检测是否需要重新订阅全量数据 bitget 系检测
        if (ExchangeCode.bitgetIn().contains(platform()) && event.getChecksum() != 0 && !checkSum(event.getChecksum(), 25)) {
            SpringUtil.getBean(RebuildOrderBookUtil.class).rebuildOrderBook(platform(), symbol());
            //清理本地缓存，等待新数据进来
            ask.clear();
            bid.clear();
            return;
        }
    }

    /**
     * 插入数据
     * @param priceFeeds 订单簿数据
     */
    private void insert(OrderBook priceFeeds) {
        synchronized (platform()) {
            handlePriceCache(priceFeeds.getAsk(), true);
            handlePriceCache(priceFeeds.getBid(), false);
        }
    }

    /**
     * 处理订单簿缓存
     * @param sideOrderBook 买卖单列表
     * @param isAsk 是否为卖单
     */
    private void handlePriceCache(List<PriceLevel> sideOrderBook, boolean isAsk) {
        if (CollUtil.isEmpty(sideOrderBook)) {
            return;
        }

        if (platform().equals(ExchangeCode.BINANCE) || platform().equals(ExchangeCode.BYBIT)) {
            ConcurrentSkipListMap<BigDecimal, BigDecimal> orderBook = isAsk ? ask : bid;
            Map<BigDecimal, BigDecimal> map = sideOrderBook.stream().collect(Collectors.toMap(
                    PriceLevel::getPrice, PriceLevel::getQuantity));
            orderBook.clear();
            orderBook.putAll(map);
            return;
        }
        ConcurrentSkipListMap<BigDecimal, BigDecimal> orderBook = isAsk ? ask : bid;
        if (CollUtil.isEmpty(orderBook)) {
            Map<BigDecimal, BigDecimal> map = sideOrderBook.stream().collect(Collectors.toMap(
                    PriceLevel::getPrice, PriceLevel::getQuantity));
            orderBook.putAll(map);
        } else {
            Set<BigDecimal> removeKeySet = new HashSet<>();
            Map<BigDecimal, BigDecimal> putKeyMap = new HashMap<>();

            sideOrderBook.forEach(a -> {
                BigDecimal price = a.getPrice();
                BigDecimal quantity = a.getQuantity();
                if (BigDecimal.ZERO.equals(quantity) && orderBook.containsKey(price)) {
                    removeKeySet.add(price);
                } else {
                    putKeyMap.put(price, quantity);
                }
            });

            //删除
            if (CollUtil.isNotEmpty(removeKeySet)) {
                orderBook.keySet().removeAll(removeKeySet);
            }

            //新增
            if (CollUtil.isNotEmpty(putKeyMap)) {
                orderBook.putAll(putKeyMap);
            }
        }
    }

    /**
     * 获取订单簿
     * @return 完整订单簿
     */
    public OrderBook get() {
        OrderBook.OrderBookBuilder orderBookBuilder = OrderBook.builder()
                .exchange(platform())
                .symbol(symbol());
        synchronized (platform()) {
            if (CollUtil.isNotEmpty(ask)) {
                List<PriceLevel> askList = ask.entrySet().stream()
                        .map(entry -> new PriceLevel(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
                orderBookBuilder.ask(askList);
            }
            if (CollUtil.isNotEmpty(bid)) {
                List<PriceLevel> bidList = bid.entrySet().stream()
                        .map(entry -> new PriceLevel(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
                orderBookBuilder.bid(bidList);
            }
        }
        return orderBookBuilder.build();
    }

    /**
     * 校验订单簿校验和
     * @param checkSum 远程校验和
     * @param gear 校验档位
     * @return 是否一致
     */
    public boolean checkSum(long checkSum, int gear) {
        try {
            StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<BigDecimal, BigDecimal>> asksIterator = ask.entrySet().iterator();
            Iterator<Map.Entry<BigDecimal, BigDecimal>> bidsIterator = bid.entrySet().iterator();
            int index = 0;
            while (index < gear) {
                if (bidsIterator.hasNext()) {
                    Map.Entry<BigDecimal, BigDecimal> next = bidsIterator.next();
                    sb.append(next.getKey()).append(":").append(next.getValue()).append(":");
                }
                if (asksIterator.hasNext()) {
                    Map.Entry<BigDecimal, BigDecimal> next = asksIterator.next();
                    sb.append(next.getKey()).append(":").append(next.getValue()).append(":");
                }
                index++;
            }
            String s = sb.toString();
            //如果拿到的拼接字符串是空的，直接进行重建order book
            if (StringUtils.isBlank(s)) {
                return false;
            }
            String str = s.substring(0, s.length() - 1);
            CRC32 crc32 = new CRC32();
            crc32.update(str.getBytes());
            int value = (int) crc32.getValue();
            return value == checkSum;
        } catch (Exception e) {
            log.info("check sum error start rebuild {}, symbol()", e);
            return false;
        }
    }
}