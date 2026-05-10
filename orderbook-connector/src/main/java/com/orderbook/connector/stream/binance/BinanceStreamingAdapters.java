package com.orderbook.connector.stream.binance;

import com.orderbook.connector.stream.binance.dto.BinanceRawTrade;
import com.orderbook.connector.stream.binance.dto.DepthBinanceWebSocketTransaction;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BinanceStreamingAdapters {

    public static Trade adaptRawTrade(BinanceRawTrade rawTrade) {
        Instrument instrument = BinanceAdapters.adaptSymbol(rawTrade.getSymbol(), false);
        return new Trade.Builder()
                .type(BinanceAdapters.convertType(rawTrade.isBuyerMarketMaker()))
                .originalAmount(rawTrade.getQuantity())
                .instrument(instrument)
                .price(rawTrade.getPrice())
                .makerOrderId(getMakerOrderId(rawTrade))
                .takerOrderId(getTakerOrderId(rawTrade))
                .timestamp(new Date(rawTrade.getTimestamp()))
                .id(String.valueOf(rawTrade.getTradeId()))
                .build();
    }

    private static String getMakerOrderId(BinanceRawTrade trade) {
        return String.valueOf(
                trade.isBuyerMarketMaker() ? trade.getBuyerOrderId() : trade.getSellerOrderId()
        );
    }

    private static String getTakerOrderId(BinanceRawTrade trade) {
        return String.valueOf(
                trade.isBuyerMarketMaker() ? trade.getSellerOrderId() : trade.getBuyerOrderId()
        );
    }

    public static OrderBook adaptFuturesOrderBook(DepthBinanceWebSocketTransaction binanceOrderBook) {
        List<LimitOrder> asks = new ArrayList<>();
        List<LimitOrder> bids = new ArrayList<>();
        Instrument instrument = BinanceAdapters.adaptSymbol(binanceOrderBook.getSymbol(), true);

        binanceOrderBook
                .getOrderBook()
                .asks
                .forEach(
                        (BigDecimal key, BigDecimal value) ->
                                asks.add(
                                        new LimitOrder.Builder(Order.OrderType.ASK, instrument)
                                                .limitPrice(key)
                                                .originalAmount(value)
                                                .build()));

        binanceOrderBook
                .getOrderBook()
                .bids
                .forEach(
                        (BigDecimal key, BigDecimal value) ->
                                bids.add(
                                        new LimitOrder.Builder(Order.OrderType.BID, instrument)
                                                .limitPrice(key)
                                                .originalAmount(value)
                                                .build()));

        return new OrderBook(Date.from(Instant.now()), asks, bids);
    }
}