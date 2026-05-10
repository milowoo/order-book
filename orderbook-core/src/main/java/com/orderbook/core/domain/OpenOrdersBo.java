package com.orderbook.core.domain;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Getter
@Data
public class OpenOrdersBo {

    public static Map<String, OpenOrdersBo> openOrdersBoMap = Maps.newConcurrentMap();

    private final String symbol;
    private final Map<Long, OrderBo> orders;
    private volatile long lastSeqNumber;

    public OpenOrdersBo(String symbol) {
        this.symbol = symbol;
        this.orders = Maps.newConcurrentMap();
    }

    public List<OrderBo> getAllOrder() {
        return Lists.newArrayList(orders.values());
    }

    public final boolean addForNew(OrderBo order) {
        if (!this.orders.containsKey(order.getOrderId())) {
            this.add(order);
            return true;
        }
        return false;
    }

    public final boolean add(OrderBo order) {
        if (order.getOrderId() > 0L) {
            this.orders.put(order.getOrderId(), order);
        } else {
            log.error("OrderId is less than zero");
        }
        return true;
    }

    public OrderBo removeOrderById(long orderId) {
        return this.orders.remove(orderId);
    }

    public boolean removeIfFilled(OrderBo order) {
        if (OrderStatus.FILLED.name().equalsIgnoreCase(order.getOrderStatus())
                || order.getRemainingQty().compareTo(BigDecimal.ZERO) <= 0) {
            removeOrderById(order.getOrderId());
            return true;
        }
        return false;
    }

    public void updateOpenOrder(OrderBo order) {
        String orderStatus = order.getOrderStatus();

        if (OrderStatus.NEW.name().equalsIgnoreCase(orderStatus)
                || OrderStatus.PENDING_NEW.name().equalsIgnoreCase(orderStatus)) {
            if (order.getRemainingQty().compareTo(BigDecimal.ZERO) > 0) {
                addForNew(order);
            }
        }

        if (OrderStatus.CANCELED.name().equalsIgnoreCase(orderStatus)
                || OrderStatus.EXPIRED.name().equalsIgnoreCase(orderStatus)
                || OrderStatus.REJECTED.name().equalsIgnoreCase(orderStatus)) {
            removeOrderById(order.getOrderId());
        }

        if (OrderStatus.PARTIALLY_FILLED.name().equalsIgnoreCase(orderStatus)
                || OrderStatus.FILLED.name().equalsIgnoreCase(orderStatus)
                || OrderStatus.PARTIALLY_CANCELED.name().equalsIgnoreCase(orderStatus)) {
            if (!removeIfFilled(order) && order.getRemainingQty().compareTo(BigDecimal.ZERO) > 0) {
                add(order);
            }
        }

        if (OrderStatus.PARTIALLY_CANCELED.name().equalsIgnoreCase(orderStatus)) {
            removeOrderById(order.getOrderId());
            if (order.getRemainingQty().compareTo(BigDecimal.ZERO) > 0) {
                add(order);
            }
        }
    }

    public void clear() {
        this.orders.clear();
    }
}