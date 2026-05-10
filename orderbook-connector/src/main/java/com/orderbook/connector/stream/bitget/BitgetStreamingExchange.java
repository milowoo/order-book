package com.orderbook.connector.stream.bitget;

import com.orderbook.connector.bitget.BitgetExchange;
import com.orderbook.connector.stream.bitget.config.Config;
import info.bitrich.xchangestream.core.*;
import io.reactivex.rxjava3.core.Completable;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.Exchange;

import java.util.ArrayList;
import java.util.List;

@Getter
public class BitgetStreamingExchange extends BitgetExchange implements StreamingExchange {

    private BitgetStreamingService publicStreamingService;
    private BitgetPrivateStreamingService privateStreamingService;
    private StreamingMarketDataService streamingMarketDataService;
    private StreamingTradeService streamingTradeService;
    private StreamingAccountService streamingAccountService;

    @Override
    public Completable connect(ProductSubscription... args) {
        List<Completable> completableList = new ArrayList<>();
        Boolean isUseSandBox = (Boolean) exchangeSpecification.getExchangeSpecificParametersItem(Exchange.USE_SANDBOX);
        publicStreamingService = new BitgetStreamingService(Config.V2_PUBLIC_WS_URL);
        if (StringUtils.isNoneBlank(
                exchangeSpecification.getApiKey(),
                exchangeSpecification.getSecretKey(),
                exchangeSpecification.getPassword())) {
            privateStreamingService =
                    new BitgetPrivateStreamingService(
                            isUseSandBox ? Config.V2_DEMO_PRIVATE_WS_URL : Config.V2_PRIVATE_WS_URL,
                            exchangeSpecification.getApiKey(),
                            exchangeSpecification.getSecretKey(),
                            exchangeSpecification.getPassword());
            streamingTradeService = new BitgetStreamingTradeService(privateStreamingService);
            completableList.add(privateStreamingService.connect());
        }
        applyStreamingSpecification(exchangeSpecification, publicStreamingService);
        streamingMarketDataService = new BitgetStreamingMarketDataService(publicStreamingService);
        completableList.add(publicStreamingService.connect());

        return Completable.concat(completableList);
    }

    @Override
    public Completable disconnect() {
        BitgetStreamingService service = publicStreamingService;
        publicStreamingService = null;
        streamingMarketDataService = null;
        streamingTradeService = null;
        streamingAccountService = null;
        return service.disconnect();
    }

    @Override
    public boolean isAlive() {
        return publicStreamingService != null && publicStreamingService.isSocketOpen();
    }

    @Override
    public void useCompressedMessages(boolean compressedMessages) {
        publicStreamingService.useCompressedMessages(compressedMessages);
    }
}