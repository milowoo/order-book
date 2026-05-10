package com.orderbook.connector.stream.bitget;

import com.orderbook.connector.bitget.BitgetAdapters;
import com.orderbook.connector.common.dto.BitgetChannel;
import com.orderbook.connector.common.dto.BitgetTickerNotification;
import com.orderbook.connector.common.dto.BitgetWsOrderBookSnapshotNotification;
import com.orderbook.connector.common.dto.BitgetWsUserTradeNotification;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ArrayUtils;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class BitgetStreamingAdapters {

    public Ticker toTicker(BitgetTickerNotification notification) {
        BitgetTickerNotification.TickerData bitgetTickerDto = notification.getPayloadItems().get(0);
        CurrencyPair currencyPair = BitgetAdapters.toCurrencyPair(bitgetTickerDto.getInstrument());
        if (currencyPair == null) {
            return null;
        }

        return new Ticker.Builder()
                .instrument(currencyPair)
                .open(bitgetTickerDto.getOpen24h())
                .last(bitgetTickerDto.getLastPrice())
                .bid(bitgetTickerDto.getBestBidPrice())
                .ask(bitgetTickerDto.getBestAskPrice())
                .high(bitgetTickerDto.getHigh24h())
                .low(bitgetTickerDto.getLow24h())
                .volume(bitgetTickerDto.getAssetVolume24h())
                .quoteVolume(bitgetTickerDto.getQuoteVolume24h())
                .timestamp(BitgetAdapters.toDate(bitgetTickerDto.getTimestamp()))
                .bidSize(bitgetTickerDto.getBestBidSize())
                .askSize(bitgetTickerDto.getBestAskSize())
                .percentageChange(bitgetTickerDto.getChange24h())
                .build();
    }

    // Returns unique subscription id. Can be used as key for subscriptions caching
    public String toSubscriptionId(BitgetChannel bitgetChannel) {
        return Stream.of(
                bitgetChannel.getMarketType(),
                bitgetChannel.getChannelType(),
                bitgetChannel.getInstrumentId()
        )
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
    }

    // Creates BitgetChannel from arguments
    // Params: args - ChannelType, MarketType, Instrument / null
    public BitgetChannel toBitgetChannel(Object... args) {
        BitgetChannel.ChannelType channelType = (BitgetChannel.ChannelType) ArrayUtils.get(args, 0);
        BitgetChannel.MarketType marketType = (BitgetChannel.MarketType) ArrayUtils.get(args, 1);
        Instrument instrument = (Instrument) ArrayUtils.get(args, 2);

        return BitgetChannel.builder()
                .channelType(channelType)
                .marketType(marketType)
                .instrumentId(
                        Optional.ofNullable(instrument).map(BitgetAdapters::toString).orElse("default")
                )
                .build();
    }

    public OrderBook toOrderBook(BitgetWsOrderBookSnapshotNotification notification, Instrument instrument) {
        BitgetWsOrderBookSnapshotNotification.OrderBookData orderBookData = notification.getPayloadItems().get(0);

        List<LimitOrder> asks = orderBookData.getAsks().stream()
                .map(priceSizeEntry ->
                        new LimitOrder(
                                Order.OrderType.ASK,
                                priceSizeEntry.getSize(),
                                instrument,
                                null,
                                null,
                                priceSizeEntry.getPrice()
                        )
                )
                .collect(Collectors.toList());

        List<LimitOrder> bids = orderBookData.getBids().stream()
                .map(priceSizeEntry ->
                        new LimitOrder(
                                Order.OrderType.BID,
                                priceSizeEntry.getSize(),
                                instrument,
                                null,
                                null,
                                priceSizeEntry.getPrice()
                        )
                )
                .collect(Collectors.toList());

        return new OrderBook(BitgetAdapters.toDate(orderBookData.getTimestamp()), asks, bids);
    }

    public UserTrade toUserTrade(BitgetWsUserTradeNotification notification) {
        BitgetWsUserTradeNotification.BitgetFillData bitgetFillData = notification.getPayloadItems().get(0);

        return new UserTrade(
                bitgetFillData.getOrderSide(),
                bitgetFillData.getAssetAmount(),
                BitgetAdapters.toCurrencyPair(bitgetFillData.getSymbol()),
                bitgetFillData.getPrice(),
                BitgetAdapters.toDate(bitgetFillData.getUpdatedAt()),
                bitgetFillData.getTradeId(),
                bitgetFillData.getOrderId(),
                bitgetFillData.getFeeDetails().stream()
                        .map(BitgetWsUserTradeNotification.FeeDetail::getTotalFee)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                bitgetFillData.getFeeDetails().stream()
                        .map(BitgetWsUserTradeNotification.FeeDetail::getCurrency)
                        .findFirst()
                        .orElse(null),
                null
        );
    }
}