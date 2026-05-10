package com.orderbook.connector.stream.bybit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.connector.stream.bybit.dto.account.BybitBalanceChangesResponse;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;

@Slf4j
public class BybitStreamingAccountService implements StreamingAccountService {
    private final BybitUserDataStreamingService streamingService;
    private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

    public BybitStreamingAccountService(BybitUserDataStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @Override
    public Observable<Balance> getBalanceChanges(Currency currency, Object... args) {
        return getBalanceChanges().filter(balance -> balance.getCurrency().equals(currency));
    }

    public Observable<Balance> getBalanceChanges() {
        String channelUniqueId = "wallet";
        return streamingService
                .subscribeChannel(channelUniqueId)
                .flatMap(node -> {
                    BybitBalanceChangesResponse bybitBalanceChangesResponse =
                            mapper.treeToValue(node, BybitBalanceChangesResponse.class);
                    return Observable.fromIterable(
                            BybitStreamAdapters.adaptBalances(bybitBalanceChangesResponse)
                    );
                });
    }
}