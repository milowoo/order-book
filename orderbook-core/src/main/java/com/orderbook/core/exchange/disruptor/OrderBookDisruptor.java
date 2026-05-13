package com.orderbook.core.exchange.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.exchange.common.AbstractSymbolOrderBooks;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.event.PriceMovementDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class OrderBookDisruptor {
    private Disruptor<OrderBook> disruptor;

    @Autowired
    private OrderBookStore orderBookStore;

    @Autowired(required = false)
    private PriceMovementDetector priceMovementDetector;

    @PostConstruct
    public void init() {
        disruptor = new Disruptor<>(OrderBook::new, 8192, DaemonThreadFactory.INSTANCE);

        List<EventHandler<OrderBook>> handlerList = new ArrayList<>();
        handlerList.addAll(orderBookStore.getHandler());
        if (priceMovementDetector != null) {
            handlerList.add(priceMovementDetector);
        }

        disruptor.handleEventsWith(handlerList.toArray(new EventHandler[0]));
        disruptor.start();
        log.info("disruptor started with {} handlers", handlerList.size());
    }

    /**
     * 事件发布
     */
    public void publish(OrderBook orderBook) {
        if (Objects.isNull(orderBook)) {
            return;
        }
        RingBuffer<OrderBook> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((OrderBook event, long sequence) -> {
            event.setExchange(orderBook.getExchange());
            event.setSymbol(orderBook.getSymbol());
            event.setAsk(orderBook.getAsk());
            event.setBid(orderBook.getBid());
            event.setChecksum(orderBook.getChecksum());
        });
    }
}