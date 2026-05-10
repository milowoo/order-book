package com.orderbook.connector.global;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.global.service.*;
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

public class GlobalExchange extends BaseExchange {

    private final String apiUrl = "http://osl-nio-spot-openapi-inner.apps:8080";
    private String host = "glb.osltest.com";

    @Override
    protected void initServices() {
        accountService = new GlobalAccountService(this);
        marketDataService = new GlobalMarketDataService(this);
        tradeService = new GlobalTradeService(this);
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        ExchangeSpecification specification = new ExchangeSpecification(getClass());
        specification.setSslUri(apiUrl);
        specification.setHost(host);
        specification.setExchangeName(ExchangeCode.OSL_GLOBAL.name());
        return specification;
    }

    @Override
    public void remoteInit() throws IOException {
        initNetCall();
    }

    private void initNetCall() {
        // initialize symbol mappings
        try {
            GlobalMarketDataServiceRaw bitgetMarketDataServiceRaw = (GlobalMarketDataServiceRaw) marketDataService;
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
        } catch (Exception e) {
            logger.warn("initNetCall {}", e.getMessage());
        }
    }
}