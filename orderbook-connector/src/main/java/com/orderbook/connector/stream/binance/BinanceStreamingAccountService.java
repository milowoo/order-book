package com.orderbook.connector.stream.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.connector.stream.binance.dto.BaseBinanceWebSocketTransaction;
import com.orderbook.connector.stream.binance.dto.OutboundAccountPositionBinanceWebsocketTransaction;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

public class BinanceStreamingAccountService implements StreamingAccountService {

    private final BehaviorSubject<OutboundAccountPositionBinanceWebsocketTransaction> accountInfoLast = BehaviorSubject.create();
    private final Subject<OutboundAccountPositionBinanceWebsocketTransaction> accountInfoPublisher = accountInfoLast.toSerialized();

    private volatile Disposable accountInfo;
    private volatile BinanceUserDataStreamingService binanceUserDataStreamingService;

    private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

    public BinanceStreamingAccountService(BinanceUserDataStreamingService binanceUserDataStreamingService) {
        this.binanceUserDataStreamingService = binanceUserDataStreamingService;
    }

    public Observable<OutboundAccountPositionBinanceWebsocketTransaction> getRawAccountInfo() {
        checkConnected();
        return accountInfoPublisher;
    }

    public Observable<Balance> getBalanceChanges() {
        checkConnected();
        return getRawAccountInfo()
                .map(OutboundAccountPositionBinanceWebsocketTransaction::toBalanceList)
                .flatMap(Observable::fromIterable);
    }

    @Override
    public Observable<Balance> getBalanceChanges(Currency currency, Object... args) {
        return getBalanceChanges().filter(t -> t.getCurrency().equals(currency));
    }

    private void checkConnected() {
        if (binanceUserDataStreamingService == null || !binanceUserDataStreamingService.isSocketOpen()) {
            throw new ExchangeSecurityException("Not authenticated");
        }
    }

    public void openSubscriptions() {
        if (binanceUserDataStreamingService != null) {
            accountInfo =
                    binanceUserDataStreamingService
                            .subscribeChannel(
                                    BaseBinanceWebSocketTransaction.BinanceWebSocketTypes.OUTBOUND_ACCOUNT_POSITION)
                            .map(this::accountInfo)
                            .filter(
                                    m -> {
                                        return accountInfoLast.getValue() == null
                                                || accountInfoLast.getValue().getEventTime().before(m.getEventTime());
                                    })
                            .subscribe(accountInfoPublisher::onNext);
        }
    }

    public void closeSubscriptions() {
        if (accountInfo != null) {
            accountInfo.dispose();
            accountInfo = null;
        }
    }

    void setUserDataStreamingService(BinanceUserDataStreamingService binanceUserDataStreamingService) {
        if (accountInfo != null && !accountInfo.isDisposed()) accountInfo.dispose();
        this.binanceUserDataStreamingService = binanceUserDataStreamingService;
        openSubscriptions();
    }

    private OutboundAccountPositionBinanceWebsocketTransaction accountInfo(JsonNode json) {
        try {
            return mapper.treeToValue(json, OutboundAccountPositionBinanceWebsocketTransaction.class);
        } catch (Exception e) {
            throw new ExchangeException("Unable to parse account info", e);
        }
    }
}