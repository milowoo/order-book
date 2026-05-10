package com.orderbook.connector.stream.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.connector.stream.binance.dto.BaseBinanceWebSocketTransaction;
import com.orderbook.connector.stream.binance.dto.ExecutionReportBinanceUserTransaction;
import com.orderbook.connector.stream.binance.dto.ExecutionReportBinanceUserTransaction.ExecutionType;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.instrument.Instrument;

import java.io.IOException;

public class BinanceStreamingTradeService implements StreamingTradeService {

    private final Subject<ExecutionReportBinanceUserTransaction> executionReportsPublisher = PublishSubject.<ExecutionReportBinanceUserTransaction>create().toSerialized();

    private volatile Disposable executionReports;
    private volatile BinanceUserDataStreamingService binanceUserDataStreamingService;

    private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

    public BinanceStreamingTradeService(BinanceUserDataStreamingService binanceUserDataStreamingService) {
        this.binanceUserDataStreamingService = binanceUserDataStreamingService;
    }

    public Observable<ExecutionReportBinanceUserTransaction> getRawExecutionReports() {
        if (binanceUserDataStreamingService == null || !binanceUserDataStreamingService.isSocketOpen()) {
            throw new ExchangeSecurityException("Not authenticated");
        }
        return executionReportsPublisher;
    }

    public Observable<Order> getOrderChanges(boolean isFuture) {
        return getRawExecutionReports()
                .filter(r -> !r.getExecutionType().equals(ExecutionType.REJECTED))
                .map(exec -> exec.toOrder(isFuture));
    }

    @Override
    public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
        return getOrderChanges(false).filter((Order oc) -> currencyPair.equals(oc.getInstrument()));
    }

    @Override
    public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
        return getUserTrades(false).filter((UserTrade t) -> t.getInstrument().equals(currencyPair));
    }

    @Override
    public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
        return getOrderChanges(instrument instanceof FuturesContract)
                .filter((Order oc) -> instrument.equals(oc.getInstrument()));
    }

    @Override
    public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
        return getUserTrades(instrument instanceof FuturesContract)
                .filter((UserTrade t) -> instrument.equals(t.getInstrument()));
    }

    public Observable<UserTrade> getUserTrades(boolean isFuture) {
        return getRawExecutionReports()
                .filter(r -> r.getExecutionType().equals(ExecutionType.TRADE))
                .map(exec -> exec.toUserTrade(isFuture));
    }

    /**
     * Registers subscriptions with the streaming service for the given products.
     */
    public void openSubscriptions() {
        if (binanceUserDataStreamingService != null) {
            executionReports =
                    binanceUserDataStreamingService
                            .subscribeChannel(
                                    BaseBinanceWebSocketTransaction.BinanceWebSocketTypes.EXECUTION_REPORT)
                            .map(this::executionReport)
                            .subscribe(executionReportsPublisher::onNext);
        }
    }

    /**
     * User data subscriptions may have to persist across multiple socket connections to different URLs and
     * therefore must act in a publisher fashion so that subscribers get an uninterrupted stream.
     */
    void setUserDataStreamingService(BinanceUserDataStreamingService binanceUserDataStreamingService) {
        if (executionReports != null && !executionReports.isDisposed()) executionReports.dispose();
        this.binanceUserDataStreamingService = binanceUserDataStreamingService;
        openSubscriptions();
    }

    private ExecutionReportBinanceUserTransaction executionReport(JsonNode json) {
        try {
            return mapper.treeToValue(json, ExecutionReportBinanceUserTransaction.class);
        } catch (IOException e) {
            throw new ExchangeException("Unable to parse execution report", e);
        }
    }
}