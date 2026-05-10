package com.orderbook.connector.stream.bybit;

import com.orderbook.connector.stream.bybit.dto.account.BybitBalanceChangesResponse;
import com.orderbook.connector.stream.bybit.dto.marketdata.BybitOrderbook;
import com.orderbook.connector.stream.bybit.dto.marketdata.BybitPublicOrder;
import com.orderbook.connector.stream.bybit.dto.trade.BybitComplexOrderChanges;
import com.orderbook.connector.stream.bybit.dto.trade.BybitComplexPositionChanges;
import com.orderbook.connector.stream.bybit.dto.trade.BybitOrderChangesResponse.BybitOrderChanges;
import com.orderbook.connector.stream.bybit.dto.trade.BybitPositionChangesResponse.BybitPositionChanges;
import com.orderbook.connector.stream.bybit.dto.trade.BybitTrade;

import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.bybit.dto.trade.details.BybitTimeInForce;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPosition.Type;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.currency.Currency;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

import static org.knowm.xchange.bybit.BybitAdapters.*;

@Slf4j
public class BybitStreamAdapters {

    public static OrderBook adaptOrderBook(BybitOrderbook bybitOrderBooks, Instrument instrument) {
        List<LimitOrder> asks = new ArrayList<>();
        List<LimitOrder> bids = new ArrayList<>();
        Date timestamp = new Date(Long.parseLong(bybitOrderBooks.getTs()));

        bybitOrderBooks
                .getData()
                .getAsk()
                .forEach(bybitAsk ->
                        asks.add(adaptOrderBookOrder(bybitAsk, instrument, OrderType.ASK, timestamp))
                );

        bybitOrderBooks
                .getData()
                .getBid()
                .forEach(bybitBid ->
                        bids.add(adaptOrderBookOrder(bybitBid, instrument, OrderType.BID, timestamp))
                );

        return new OrderBook(timestamp, asks, bids);
    }

    public static Trades adaptTrades(List<BybitTrade> bybitTrades, Instrument instrument) {
        List<Trade> trades = new ArrayList<>();
        bybitTrades.forEach(bybitTrade ->
                trades.add(
                        new Trade.Builder()
                                .id(bybitTrade.getTradeId())
                                .instrument(instrument)
                                .originalAmount(bybitTrade.getTradeSize())
                                .price(bybitTrade.getTradePrice())
                                .timestamp(bybitTrade.getTimestamp())
                                .type(getOrderType(bybitTrade.getSide()))
                                .build()
                )
        );
        return new Trades(trades);
    }

    public static LimitOrder adaptOrderBookOrder(
            BybitPublicOrder bybitPublicOrder,
            Instrument instrument,
            OrderType orderType,
            Date timestamp
    ) {
        return new LimitOrder(
                orderType,
                new BigDecimal(bybitPublicOrder.getSize()),
                instrument,
                "",
                timestamp,
                new BigDecimal(bybitPublicOrder.getPrice())
        );
    }

    public static List<Order> adaptOrdersChanges(List<BybitOrderChanges> bybitOrderChanges) {
        Date date = new Date();
        List<Order> orders = new ArrayList<>();

        for (BybitOrderChanges bybitOrderChange : bybitOrderChanges) {
            date.setTime(Long.parseLong(bybitOrderChange.getUpdatedTime()));
            OrderType orderType = getOrderType(bybitOrderChange.getSide());
            Order.Builder builder = null;

            switch (bybitOrderChange.getOrderType()) {
                case LIMIT:
                    builder = new LimitOrder.Builder(
                            orderType,
                            convertBybitSymbolToInstrument(bybitOrderChange.getSymbol(), bybitOrderChange.getCategory())
                    ).limitPrice(new BigDecimal(bybitOrderChange.getPrice()));
                    break;
                case MARKET:
                    builder = new MarketOrder.Builder(
                            orderType,
                            convertBybitSymbolToInstrument(bybitOrderChange.getSymbol(), bybitOrderChange.getCategory())
                    );
                    break;
            }

            if (!bybitOrderChange.getAvgPrice().isEmpty()) {
                builder.averagePrice(new BigDecimal(bybitOrderChange.getAvgPrice()));
            }

            builder
                    .fee(new BigDecimal(bybitOrderChange.getCumExecFee()))
                    .id(bybitOrderChange.getOrderId())
                    .orderStatus(adaptBybitOrderStatus(bybitOrderChange.getOrderStatus()))
                    .timestamp(date)
                    .cumulativeAmount(new BigDecimal(bybitOrderChange.getCumExecQty()))
                    .originalAmount(new BigDecimal(bybitOrderChange.getQty()))
                    .id(bybitOrderChange.getOrderId())
                    .userReference(bybitOrderChange.getOrderLinkId());

            orders.add(builder.build());
        }
        return orders;
    }

    public static OpenPositions adaptPositionChanges(List<BybitPositionChanges> bybitPositionChanges) {
        OpenPositions openPositions = new OpenPositions(new ArrayList<>());
        for (BybitPositionChanges position : bybitPositionChanges) {
            Type type = getPositionType(position);
            BigDecimal liqPrice = getLiqPrice(position);
            if (position.getLiqPrice() != null && !position.getLiqPrice().isEmpty()) {
                liqPrice = new BigDecimal(position.getLiqPrice());
            }

            OpenPosition openPosition = new OpenPosition(
                    convertBybitSymbolToInstrument(position.getSymbol(), position.getCategory()),
                    type,
                    new BigDecimal(position.getSize()),
                    new BigDecimal(position.getEntryPrice()),
                    liqPrice,
                    new BigDecimal(position.getUnrealisedPnl())
            );
            openPositions.getOpenPositions().add(openPosition);
        }
        return openPositions;
    }

    private static Type getPositionType(BybitPositionChanges position) {
        if (position.getSide() != null && !position.getSide().isEmpty()) {
            return position.getSide().equals("Buy") ? Type.LONG : Type.SHORT;
        }
        return null;
    }

    private static BigDecimal getLiqPrice(BybitPositionChanges position) {
        if (position.getLiqPrice() != null && !position.getLiqPrice().isEmpty()) {
            return new BigDecimal(position.getLiqPrice());
        }
        return null;
    }

    public static List<BybitComplexPositionChanges> adaptComplexPositionChanges(List<BybitPositionChanges> data) {
        List<BybitComplexPositionChanges> result = new ArrayList<>();
        for (BybitPositionChanges position : data) {
            Type type = getPositionType(position);
            BigDecimal liqPrice = getLiqPrice(position);
            BigDecimal bustPrice = null;
            if (position.getBustPrice() != null && !position.getBustPrice().isEmpty()) {
                bustPrice = new BigDecimal(position.getBustPrice());
            }

            BigDecimal sessionAvgPrice = null;
            if (position.getSessionAvgPrice() != null && !position.getSessionAvgPrice().isEmpty()) {
                sessionAvgPrice = new BigDecimal(position.getSessionAvgPrice());
            }

            BybitComplexPositionChanges positionChanges = new BybitComplexPositionChanges(
                    convertBybitSymbolToInstrument(position.getSymbol(), position.getCategory()),
                    type,
                    new BigDecimal(position.getSize()),
                    new BigDecimal(position.getEntryPrice()),
                    liqPrice,
                    new BigDecimal(position.getUnrealisedPnl()),
                    position.getPositionIdx(),
                    position.getTradeMode(),
                    position.getRiskId(),
                    position.getRiskLimitValue(),
                    new BigDecimal(position.getMarkPrice()),
                    new BigDecimal(position.getPositionBalance()),
                    position.getAutoAddMargin(),
                    new BigDecimal(position.getPositionMM()),
                    new BigDecimal(position.getPositionIM()),
                    bustPrice,
                    new BigDecimal(position.getPositionValue()),
                    new BigDecimal(position.getLeverage()),
                    new BigDecimal(position.getTakeProfit()),
                    new BigDecimal(position.getStopLoss()),
                    new BigDecimal(position.getTrailingStop()),
                    new BigDecimal(position.getCurRealisedPnl()),
                    new BigDecimal(position.getCumRealisedPnl()),
                    sessionAvgPrice,
                    position.getPositionStatus(),
                    position.getAdlRankIndicator(),
                    position.isReduceOnly(),
                    position.getMmrSysUpdatedTime(),
                    position.getLeverageSysUpdatedTime(),
                    new Date(Long.parseLong(position.getCreatedTime())),
                    new Date(Long.parseLong(position.getUpdatedTime())),
                    position.getSeq()
            );
            result.add(positionChanges);
        }
        return result;
    }

    public static List<BybitComplexOrderChanges> adaptComplexOrdersChanges(List<BybitOrderChanges> data) {
        List<BybitComplexOrderChanges> result = new ArrayList<>();
        for (BybitOrderChanges change : data) {
            OrderType type = getOrderType(change.getSide());
            BigDecimal avgPrice = change.getAvgPrice().isEmpty() ? null : new BigDecimal(change.getAvgPrice());
            BigDecimal triggerPrice = change.getTriggerPrice().isEmpty() ? null : new BigDecimal(change.getTriggerPrice());
            BigDecimal takeProfit = change.getTakeProfit().isEmpty() ? null : new BigDecimal(change.getTakeProfit());
            BigDecimal stopLoss = change.getStopLoss().isEmpty() ? null : new BigDecimal(change.getStopLoss());

            BybitComplexOrderChanges orderChanges = new BybitComplexOrderChanges(
                    type,
                    new BigDecimal(change.getQty()),
                    convertBybitSymbolToInstrument(change.getSymbol(), change.getCategory()),
                    change.getOrderLinkId(),
                    new Date(Long.parseLong(change.getCreatedTime())),
                    avgPrice,
                    new BigDecimal(change.getCumExecQty()),
                    new BigDecimal(change.getCumExecFee()),
                    adaptBybitOrderStatus(change.getOrderStatus()),
                    change.getCategory(),
                    change.getOrderId(),
                    change.getIsLeverage(),
                    change.getBlockTradeId(),
                    new BigDecimal(change.getPrice()),
                    new BigDecimal(change.getQty()),
                    change.getSide(),
                    change.getPositionIdx(),
                    change.getCreateType(),
                    change.getCancelType(),
                    change.getRejectReason(),
                    change.getLeavesQty().isEmpty() ? null : new BigDecimal(change.getLeavesQty()),
                    change.getLeavesValue().isEmpty() ? null : new BigDecimal(change.getLeavesValue()),
                    new BigDecimal(change.getCumExecValue()),
                    change.getFeeCurrency(),
                    BybitTimeInForce.valueOf(change.getTimeInForce().toUpperCase()),
                    change.getOrderType(),
                    change.getStopOrderType(),
                    change.getOcoTriggerBy(),
                    change.getOrderIv(),
                    change.getMarketUnit(),
                    triggerPrice,
                    takeProfit,
                    stopLoss,
                    change.getTpslMode(),
                    change.getTpLimitPrice().isEmpty() ? null : new BigDecimal(change.getTpLimitPrice()),
                    change.getSlLimitPrice().isEmpty() ? null : new BigDecimal(change.getSlLimitPrice()),
                    change.getTpTriggerBy(),
                    change.getSlTriggerBy(),
                    change.getTriggerDirection(),
                    change.getTriggerBy(),
                    change.getLastPriceOnCreated(),
                    change.isReduceOnly(),
                    change.isCloseOnTrigger(),
                    change.getPlaceType(),
                    change.getSmpType(),
                    change.getSmpGroup(),
                    change.getSmpOrderId(),
                    new Date(Long.parseLong(change.getUpdatedTime()))
            );
            result.add(orderChanges);
        }
        return result;
    }

    public static List<Balance> adaptBalances(BybitBalanceChangesResponse response) {
        if (response == null || response.getCoins() == null || response.getCoins().isEmpty()) {
            return Collections.emptyList();
        }

        List<Balance> balances = response.getCoins().stream()
                .map(coinData -> {
                    try {
                        Currency currency = Currency.getInstance(coinData.getCoin());
                        BigDecimal total = coinData.getWalletBalance();
                        BigDecimal locked = coinData.getLocked();
                        BigDecimal frozen = locked;
                        BigDecimal available = total.subtract(locked);
                        Date lastTime = response.getTimestamp();

                        return new Balance(
                                currency,
                                total,
                                available,
                                frozen,
                                BigDecimal.ZERO, // depositInterest
                                BigDecimal.ZERO, // withdrawInterest
                                BigDecimal.ZERO, // loan
                                BigDecimal.ZERO, // interest
                                lastTime
                        );
                    } catch (Exception e) {
                        log.error("Failed to adapt balance for coin: {}", coinData.getCoin(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return balances;
    }
}