package com.orderbook.connector.stream.global;

import com.orderbook.connector.common.dto.BitgetChannel;
import com.orderbook.connector.common.dto.BitgetTickerNotification;
import com.orderbook.connector.common.dto.BitgetWsOrderBookSnapshotNotification;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;

public class GlobalStreamingMarketDataService implements StreamingMarketDataService {

    private final GlobalStreamingService service;

    public GlobalStreamingMarketDataService(GlobalStreamingService service) {
        this.service = service;
    }

    // Params: currencyPair - Currency pair of the order book
    // args - Order book level: Integer 1, 5 or 15
    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        Integer orderBookLevel = (Integer) ArrayUtils.get(args, 0, null);
        Validate.notNull(orderBookLevel, "message: \"Not implemented\"");
        Validate.inclusiveBetween(0, 15, orderBookLevel, "message: \"Not implemented\"");

        BitgetChannel.ChannelType channelType;
        if (orderBookLevel == 0) {
            channelType = BitgetChannel.ChannelType.DEPTH;
        } else if (orderBookLevel == 1) {
            channelType = BitgetChannel.ChannelType.DEPTH1;
        } else if (orderBookLevel <= 5) {
            channelType = BitgetChannel.ChannelType.DEPTH5;
        } else {
            channelType = BitgetChannel.ChannelType.DEPTH15;
        }

        return service
                .subscribeChannel(null, channelType, BitgetChannel.MarketType.SPOT, currencyPair)
                .map(BitgetWsOrderBookSnapshotNotification.class::cast)
                .map(notification -> GlobalStreamingAdapters.toOrderBook(notification, currencyPair));
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
        return service
                .subscribeChannel(null, BitgetChannel.ChannelType.TICKER, BitgetChannel.MarketType.SPOT, currencyPair)
                .map(BitgetTickerNotification.class::cast)
                .map(GlobalStreamingAdapters::toTicker);
    }
}