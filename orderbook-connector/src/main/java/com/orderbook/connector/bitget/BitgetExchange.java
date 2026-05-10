package com.orderbook.connector.bitget;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.bitget.service.BitgetAccountService;
import com.orderbook.connector.bitget.service.BitgetMarketDataService;
import com.orderbook.connector.bitget.service.BitgetMarketDataServiceRaw;
import com.orderbook.connector.bitget.service.BitgetTradeService;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitget.BitgetAdapters;
import org.knowm.xchange.bitget.dto.marketdata.BitgetSymbolDto;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BitgetExchange extends BaseExchange {

    private String apiUrl = "https://api.bitget.com";
    private String host = "www.bitget.com";

    @Override
    protected void initServices() {
        accountService = new BitgetAccountService(this);
        marketDataService = new BitgetMarketDataService(this);
        tradeService = new BitgetTradeService(this);
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        ExchangeSpecification specification = new ExchangeSpecification(getClass());
        specification.setSslUri(apiUrl);
        specification.setHost(host);
        specification.setExchangeName(ExchangeCode.BITGET.name()); // todo replace
        return specification;
    }

    @Override
    public void remoteInit() throws IOException {
        BitgetMarketDataServiceRaw bitgetMarketDataServiceRaw =
                (BitgetMarketDataServiceRaw) marketDataService;

        // initialize symbol mappings
        List<BitgetSymbolDto> bitgetSymbolDtos = bitgetMarketDataServiceRaw.getBitgetSymbolDtos(null);
        bitgetSymbolDtos.forEach(
                bitgetSymbolDto -> {
                    BitgetAdapters.putSymbolMapping(
                            bitgetSymbolDto.getSymbol(), bitgetSymbolDto.getCurrencyPair());
                });

        // initialize instrument metadata
        Map<Instrument, InstrumentMetaData> instruments =
                bitgetSymbolDtos.stream()
                        .collect(
                                Collectors.toMap(
                                        BitgetSymbolDto::getCurrencyPair, BitgetAdapters::toInstrumentMetaData));

        exchangeMetaData = new ExchangeMetaData(instruments, null, null, null, null);
    }
}