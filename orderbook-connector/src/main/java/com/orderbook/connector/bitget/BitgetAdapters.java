package com.orderbook.connector.bitget;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.common.dto.*;
import com.orderbook.connector.common.convert.OrderStatusConvert;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.bitget.dto.account.BitgetAccountType;
import org.knowm.xchange.bitget.dto.account.BitgetBalanceDto;
import org.knowm.xchange.bitget.dto.account.BitgetDepositWithdrawRecordDto;
import org.knowm.xchange.bitget.dto.account.BitgetDepositWithdrawRecordDto.DepositType;
import org.knowm.xchange.bitget.dto.account.BitgetDepositWithdrawRecordDto.RecordType;
import org.knowm.xchange.bitget.dto.account.params.BitgetMainSubTransferHistoryParams;
import org.knowm.xchange.bitget.dto.account.params.BitgetMainSubTransferHistoryParams.Role;
import org.knowm.xchange.bitget.dto.marketdata.BitgetMarketDepthDto;
import org.knowm.xchange.bitget.dto.marketdata.BitgetTickerDto;
import org.knowm.xchange.bitget.dto.trade.BitgetFillDto;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto.BitgetOrderStatus;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.FundingRecord.Type;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.orderbook.connector.common.constants.Constants.QUOTE_CURRENCIES;

@UtilityClass
public class BitgetAdapters {

    private final Map<String, CurrencyPair> SYMBOL_TO_CURRENCY_PAIR = new HashMap<>();

    public CurrencyPair toCurrencyPair(String symbol) {
        return SYMBOL_TO_CURRENCY_PAIR.get(symbol);
    }

    public String toString(Instrument instrument) {
        return instrument == null
                ? null
                : instrument.getBase().toString() + instrument.getCounter().toString();
    }

    public String toString(Currency currency) {
        return Optional.ofNullable(currency).map(Currency::getCurrencyCode).orElse(null);
    }

    public void putSymbolMapping(String symbol, CurrencyPair currencyPair) {
        SYMBOL_TO_CURRENCY_PAIR.put(symbol, currencyPair);
    }

    public Ticker toTicker(BitgetTickerDto bitgetTickerDto) {
        CurrencyPair currencyPair = toCurrencyPair(bitgetTickerDto.getSymbol());
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
                .timestamp(toDate(bitgetTickerDto.getTimestamp()))
                .bidSize(bitgetTickerDto.getBestBidSize())
                .askSize(bitgetTickerDto.getBestAskSize())
                .percentageChange(bitgetTickerDto.getChange24h())
                .build();
    }

    public Date toDate(Instant instant) {
        return Optional.ofNullable(instant).map(Date::from).orElse(null);
    }

    public Balance toBalance(BitgetBalanceDto balance) {
        return new Balance.Builder()
                .currency(balance.getCurrency())
                .available(balance.getAvailable())
                .frozen(balance.getFrozen())
                .timestamp(toDate(balance.getTimestamp()))
                .build();
    }

    public Wallet toWallet(List<BitgetBalanceDto> bitgetBalanceDtos) {
        List<Balance> balances =
                bitgetBalanceDtos.stream().map(BitgetAdapters::toBalance).collect(Collectors.toList());

        return Wallet.Builder.from(balances)
                .id("spot")
                .features(EnumSet.of(Wallet.WalletFeature.TRADING))
                .build();
    }

    public OrderBook toOrderBook(BitgetMarketDepthDto bitgetMarketDepthDto, Instrument instrument) {
        List<LimitOrder> asks =
                bitgetMarketDepthDto.getAsks().stream()
                        .map(
                                priceSizeEntry ->
                                        new LimitOrder(
                                                OrderType.ASK,
                                                priceSizeEntry.getSize(),
                                                instrument,
                                                null,
                                                null,
                                                priceSizeEntry.getPrice()))
                        .collect(Collectors.toList());

        List<LimitOrder> bids =
                bitgetMarketDepthDto.getBids().stream()
                        .map(
                                priceSizeEntry ->
                                        new LimitOrder(
                                                OrderType.BID,
                                                priceSizeEntry.getSize(),
                                                instrument,
                                                null,
                                                null,
                                                priceSizeEntry.getPrice()))
                        .collect(Collectors.toList());

        return new OrderBook(toDate(bitgetMarketDepthDto.getTimestamp()), asks, bids);
    }

    public Order toOrder(BitgetOrderInfoDto order) {
        if (order == null) {
            return null;
        }

        Instrument instrument = toCurrencyPair(order.getSymbol());
        Objects.requireNonNull(instrument);
        OrderType orderType = order.getOrderSide();

        Order.Builder builder;
        switch (order.getOrderType()) {
            case MARKET:
                builder = new MarketOrder.Builder(orderType, instrument);
                break;
            case LIMIT:
                builder = new LimitOrder.Builder(orderType, instrument).limitPrice(order.getPrice());
                break;
            default:
                throw new IllegalArgumentException("Can't map " + order.getOrderType());
        }

        if (orderType == OrderType.BID) {
            // buy orders fill quote
            builder.cumulativeAmount(order.getQuoteVolume());
        } else if (orderType == OrderType.ASK) {
            // sell orders fill asset
            builder.cumulativeAmount(order.getBaseVolume());
        } else {
            throw new IllegalArgumentException("Can't map " + orderType);
        }

        BigDecimal fee = order.getFee();
        if (fee != null) {
            builder.fee(fee);
        }

        return builder
                .id(String.valueOf(order.getOrderId()))
                .averagePrice(order.getPriceAvg())
                .originalAmount(order.getSize())
                .userReference(order.getClientOid())
                .timestamp(toDate(order.getCreatedAt()))
                .orderStatus(toOrderStatus(order.getOrderStatus()))
                .build();
    }

    public OrderStatus toOrderStatus(BitgetOrderStatus bitgetOrderStatus) {
        switch (bitgetOrderStatus) {
            case PENDING:
                return OrderStatus.NEW;
            case PARTIALLY_FILLED:
                return OrderStatus.PARTIALLY_FILLED;
            case FILLED:
                return OrderStatus.FILLED;
            case CANCELLED:
                return OrderStatus.CANCELED;
            default:
                throw new IllegalArgumentException("Can't map " + bitgetOrderStatus);
        }
    }

    public BitgetPlaceOrderDto toBitgetPlaceOrderDto(MarketOrder marketOrder) {
        return BitgetPlaceOrderDto.builder()
                .symbol(toString(marketOrder.getInstrument()))
                .orderSide(marketOrder.getType())
                .orderType(BitgetOrderInfoDto.OrderType.MARKET)
                .clientOid(marketOrder.getUserReference())
                .size(marketOrder.getOriginalAmount())
                .build();
    }

    public BitgetPlaceOrderDto toBitgetPlaceOrderDto(LimitOrder limitOrder) {
        BitgetPlaceOrderDto.TimeInForce timeInForce = getOrderFlag(limitOrder, BitgetPlaceOrderDto.TimeInForce.class)
                .orElse(BitgetPlaceOrderDto.TimeInForce.GOOD_TIL_CANCELLED);

        return BitgetPlaceOrderDto.builder()
                .price(limitOrder.getLimitPrice())
                .symbol(toString(limitOrder.getInstrument()))
                .orderSide(limitOrder.getType())
                .orderType(BitgetOrderInfoDto.OrderType.LIMIT)
                .clientOid(limitOrder.getUserReference())
                .size(limitOrder.getOriginalAmount())
                .timeInForce(timeInForce)
                .build();
    }

    public UserTrade toUserTrade(BitgetFillDto bitgetFillDto) {
        return new UserTrade(
                bitgetFillDto.getOrderSide(),
                bitgetFillDto.getAssetAmount(),
                toCurrencyPair(bitgetFillDto.getSymbol()),
                bitgetFillDto.getPrice(),
                toDate(bitgetFillDto.getUpdatedAt()),
                bitgetFillDto.getTradeId(),
                bitgetFillDto.getOrderId(),
                bitgetFillDto.getFeeDetail().getTotalFee().abs(),
                bitgetFillDto.getFeeDetail().getCurrency(),
                null);
    }

    public String toString(BitgetAccountType bitgetAccountType) {
        return Optional.ofNullable(bitgetAccountType).map(BitgetAccountType::getValue).orElse(null);
    }

    public String toString(Role role) {
        return Optional.ofNullable(role).map(Role::getValue).orElse(null);
    }

    public FundingRecord toFundingRecord(BitgetDepositWithdrawRecordDto record) {
        return new FundingRecord.Builder()
                .setInternalId(record.getOrderId())
                .setBlockchainTransactionHash(record.getTradeId())
                .setCurrency(record.getCurrency())
                .setType(toFundingRecordType(record))
                .setAmount(record.getSize())
                .setFee(record.getFee())
                .setStatus(record.getStatus())
                .setAddress(record.getToAddress())
                .setAddressTag(record.getToAddressTag())
                .setDate(toDate(record.getUpdatedAt()))
                .build();
    }

    public Type toFundingRecordType(BitgetDepositWithdrawRecordDto record) {
        if (record.getDepositType() == DepositType.ON_CHAIN
                && record.getType() == RecordType.WITHDRAW) {
            return Type.WITHDRAWAL;
        }
        if (record.getDepositType() == DepositType.ON_CHAIN
                && record.getType() == RecordType.DEPOSIT) {
            return Type.DEPOSIT;
        }
        if (record.getDepositType() == DepositType.INTERNAL_TRANSFER
                && record.getType() == RecordType.WITHDRAW) {
            return Type.INTERNAL_WITHDRAWAL;
        }
        if (record.getDepositType() == DepositType.INTERNAL_TRANSFER
                && record.getType() == RecordType.DEPOSIT) {
            return Type.INTERNAL_DEPOSIT;
        }
        return null;
    }

    private <T extends Order.IOrderFlags> Optional<T> getOrderFlag(Order order, Class<T> clazz) {
        return (Optional<T>) order.getOrderFlags().stream()
                .filter(flag -> clazz.isAssignableFrom(flag.getClass()))
                .findFirst();
    }

    public static BitgetCancelOrderDto toBitgetCancelOrderDto(BitgetCancelOrderParam params) {
        if (StringUtils.isAllBlank(params.getOrderId(), params.getUserReference())) {
            throw new RuntimeException("orderId and userReference cannot be all empty");
        }

        String symbol = params.getCurrencyPair().toString().replace("/", "");
        BitgetCancelOrderDto.BitgetCancelOrderDtoBuilder bitgetCancelOrderDtoBuilder = StringUtils.isNotBlank(params.getOrderId())
                ? BitgetCancelOrderDto.builder().orderId(params.getOrderId())
                : BitgetCancelOrderDto.builder().clientOrderId(params.getUserReference());
        bitgetCancelOrderDtoBuilder.symbol(symbol);
        return bitgetCancelOrderDtoBuilder.build();
    }

    public static BitgetOrderQueryDto toBitgetOrderQueryDto(BitgetOrderQueryParam param) {
        if (Objects.isNull(param)) {
            return BitgetOrderQueryDto.builder().build();
        }

        BitgetOrderQueryDto.BitgetOrderQueryDtoBuilder builder = BitgetOrderQueryDto.builder();

        if (StringUtils.isNotBlank(param.getOrderId())) {
            builder.orderId(param.getOrderId());
        }

        if (Objects.nonNull(param.getCurrencyPair())) {
            builder.symbol(param.getCurrencyPair().toString().replace("/", ""));
        }

        if (StringUtils.isNotBlank(param.getStartTime()) && StringUtils.isNumeric(param.getStartTime())) {
            builder.startTime(Long.valueOf(param.getStartTime()));
        }

        if (StringUtils.isNotBlank(param.getEndTime()) && StringUtils.isNumeric(param.getEndTime())) {
            builder.endTime(Long.valueOf(param.getEndTime()));
        }

        if (StringUtils.isNotBlank(param.getIdLessThan())) {
            builder.idLessThan(param.getIdLessThan());
        }

        if (StringUtils.isNotBlank(param.getLimit()) && StringUtils.isNumeric(param.getLimit())) {
            builder.limit(Integer.valueOf(param.getLimit()));
        }

        if (StringUtils.isNotBlank(param.getTpslType())) {
            builder.tpslType(param.getTpslType());
        }

        if (StringUtils.isNotBlank(param.getRequestTime())) {
            builder.requestTime(param.getRequestTime());
        }

        if (StringUtils.isNotBlank(param.getReceiveWindow())) {
            builder.receiveWindow(param.getReceiveWindow());
        }

        return builder.build();
    }

    public static OpenOrders toOpenOrders(List<BitgetOrderInfoDto> bitgetOrderInfoDtos) {
        if (CollUtil.isEmpty(bitgetOrderInfoDtos)) {
            return null;
        }

        List<LimitOrder> limitOrders = new ArrayList<>();
        List<Order> orders = new ArrayList<>();

        bitgetOrderInfoDtos.forEach(
                bitgetOrderInfoDto -> {
                    if (bitgetOrderInfoDto.getOrderType().equals(BitgetOrderInfoDto.OrderType.LIMIT)) {
                        LimitOrder.Builder builder =
                                new LimitOrder.Builder(
                                        bitgetOrderInfoDto.getOrderSide(),
                                        guessSymbol(bitgetOrderInfoDto.getSymbol()))
                                        .limitPrice(
                                                bitgetOrderInfoDto.getPrice() == null
                                                        ? bitgetOrderInfoDto.getPriceAvg()
                                                        : bitgetOrderInfoDto.getPrice());
                        BigDecimal price =
                                bitgetOrderInfoDto.getPrice() == null
                                        ? bitgetOrderInfoDto.getPriceAvg()
                                        : bitgetOrderInfoDto.getPrice();
                        LimitOrder limitOrder =
                                builder.timestamp(DateUtil.date(bitgetOrderInfoDto.getUpdatedAt()))
                                        .originalAmount(bitgetOrderInfoDto.getSize())
                                        .cumulativeAmount(bitgetOrderInfoDto.getQuoteVolume())
                                        .remainingAmount(
                                                bitgetOrderInfoDto.getSize().multiply(price).subtract(bitgetOrderInfoDto.getQuoteVolume()))
                                        .id(bitgetOrderInfoDto.getOrderId())
                                        .userReference(bitgetOrderInfoDto.getClientOid())
                                        .averagePrice(bitgetOrderInfoDto.getPriceAvg())
                                        .orderStatus(
                                                OrderStatusConvert.convert(
                                                        bitgetOrderInfoDto.getOrderStatus().name(),
                                                        ExchangeCode.BITGET))
                                        .fee(bitgetOrderInfoDto.getFee())
                                        .build();
                        limitOrders.add(limitOrder);
                    } else {
                        MarketOrder.Builder builder =
                                new MarketOrder.Builder(
                                        bitgetOrderInfoDto.getOrderSide(),
                                        guessSymbol(bitgetOrderInfoDto.getSymbol()))
                                        .timestamp(DateUtil.date(bitgetOrderInfoDto.getUpdatedAt()))
                                        .originalAmount(bitgetOrderInfoDto.getSize().multiply(bitgetOrderInfoDto.getPrice()));
                        MarketOrder order =
                                builder.id(bitgetOrderInfoDto.getOrderId())
                                        .userReference(bitgetOrderInfoDto.getClientOid())
                                        .averagePrice(bitgetOrderInfoDto.getPriceAvg())
                                        .orderStatus(
                                                OrderStatusConvert.convert(
                                                        bitgetOrderInfoDto.getOrderStatus().name(),
                                                        ExchangeCode.BITGET))
                                        .fee(bitgetOrderInfoDto.getFee())
                                        .build();
                        orders.add(order);
                    }
                });

        return new OpenOrders(limitOrders, orders);
    }

    public static CurrencyPair guessSymbol(String symbol) {
        // SPOT Only
        for (String quoteCurrency : QUOTE_CURRENCIES) {
            if (symbol.endsWith(quoteCurrency)) {
                int splitIndex = symbol.lastIndexOf(quoteCurrency);
                return new CurrencyPair(symbol.substring(0, splitIndex), symbol.substring(splitIndex));
            }
        }
        int splitIndex = symbol.length() - 3;
        return new CurrencyPair(symbol.substring(0, splitIndex), symbol.substring(splitIndex));
    }
}