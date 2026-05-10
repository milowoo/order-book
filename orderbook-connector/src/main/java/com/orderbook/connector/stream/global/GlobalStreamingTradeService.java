package com.orderbook.connector.stream.global;

import com.orderbook.connector.common.dto.BitgetChannel;
import com.orderbook.connector.common.dto.BitgetWsUserTradeNotification;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Observable;
import lombok.AllArgsConstructor;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;

@AllArgsConstructor
public class GlobalStreamingTradeService implements StreamingTradeService {

    private final GlobalStreamingService service;

    @Override
    public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
        return service
                .subscribeChannel(null, BitgetChannel.ChannelType.FILL, BitgetChannel.MarketType.SPOT, currencyPair)
                .map(BitgetWsUserTradeNotification.class::cast)
                .map(GlobalStreamingAdapters::toUserTrade);
    }

    @Override
    public Observable<UserTrade> getUserTrades() {
        return getUserTrades(null);
    }
}