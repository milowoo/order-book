package com.orderbook.cmd;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum ExchangeFunc {
    READ_PACKAGE("read_package"),
    SHUFFLE_ALGORITHM("shuffle_algorithm"),

    //account
    AVAIL_BALANCE("avail_balance"),

    //instrument
    TICK_SIZE("tick_size"),
    STEP_SIZE("step_size"),
    MIN_SIZE("min_size"),

    //price
    LAST_PRICE("last_price"),
    MIX_BUY_PRICE("mix_buy_price"),
    LAST_PRICE_UPDATE_TIME("last_price_update_time"),

    //order
    PLACE_ORDER("place_order"),
    CANCEL_ORDER("cancel_order"),
    CANCEL_ORDER_BETWEEN("cancel_order_between"),
    CANCEL_ORDER_BOOK_OUT_ORDER("cancel_order_book_out_order"),
    CANCEL_ORDER_BY_SIDE("cancel_order_by_side"),
    ORDERS_MAKER("orders_maker"),

    //order book
    NOTIONAL_RANGE("notional_range"),
    NOTIONAL_NO_BOT("notional_no_bot"),
    BASE_VOLUME_RANGE("base_volume_range"),
    PRICE_LEVELS("price_level_range"),
    BEST_PRICE("best_price"),
    KLINE_PRICE("kline_price"),
    MAX_SELL_PRICE("max_sell_price"),
    MIN_BUY_PRICE("min_buy_price"),

    // open orders
    OPEN_ORDERS_OBJ("open_orders_obj"),
    OPEN_ORDERS_NOTIONAL("open_orders_notional"),
    OPEN_ORDERS_AT("open_orders_at");

    private String name;

    ExchangeFunc(String name) {
        this.name = name;
    }
}