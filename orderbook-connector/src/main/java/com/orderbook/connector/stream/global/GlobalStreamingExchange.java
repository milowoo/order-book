package com.orderbook.connector.stream.global;

import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.stream.global.config.Config;
import info.bitrich.xchangestream.core.*;
import io.reactivex.rxjava3.core.Completable;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.Exchange;

import java.util.ArrayList;
import java.util.List;

@Getter
public class GlobalStreamingExchange extends GlobalExchange implements StreamingExchange {

    private GlobalStreamingService publicStreamingService;
    private GlobalPrivateStreamingService privateStreamingService;
    private StreamingMarketDataService streamingMarketDataService;
    private StreamingTradeService streamingTradeService;
    private StreamingAccountService streamingAccountService;

    @Override
    public Completable connect(ProductSubscription... args) {
        List<Completable> completableList = new ArrayList<>();
        Boolean isUseSandBox = (Boolean) exchangeSpecification.getExchangeSpecificParametersItem(Exchange.USE_SANDBOX);
        publicStreamingService = new GlobalStreamingService(Config.V2_PUBLIC_WS_URL);
        if (StringUtils.isNoneBlank(
                exchangeSpecification.getApiKey(),
                exchangeSpecification.getSecretKey(),
                exchangeSpecification.getPassword())) {
            privateStreamingService =
                    new GlobalPrivateStreamingService(
                            isUseSandBox ? Config.V2_DEMO_PRIVATE_WS_URL : Config.V2_PRIVATE_WS_URL,
                            exchangeSpecification.getApiKey(),
                            exchangeSpecification.getSecretKey(),
                            exchangeSpecification.getPassword());
            streamingTradeService = new GlobalStreamingTradeService(privateStreamingService);
            completableList.add(privateStreamingService.connect());
        }
        applyStreamingSpecification(exchangeSpecification, publicStreamingService);
        streamingMarketDataService = new GlobalStreamingMarketDataService(publicStreamingService);
        completableList.add(publicStreamingService.connect());

        return Completable.concat(completableList);
    }

    public Completable connectPrivate() {
        Boolean isUseSandBox = (Boolean) exchangeSpecification.getExchangeSpecificParametersItem(Exchange.USE_SANDBOX);
        privateStreamingService = new GlobalPrivateStreamingService(
                isUseSandBox ? Config.V2_DEMO_PRIVATE_WS_URL : Config.V2_PRIVATE_WS_URL,
                exchangeSpecification.getApiKey(),
                exchangeSpecification.getSecretKey(),
                exchangeSpecification.getPassword());
        return privateStreamingService.connect();
    }

    public Completable connectPublic() {
        Boolean isUseSandBox = (Boolean) exchangeSpecification.getExchangeSpecificParametersItem(Exchange.USE_SANDBOX);
        publicStreamingService = new GlobalStreamingService(isUseSandBox ? Config.V2_DEMO_PUBLIC_WS_URL : Config.V2_PUBLIC_WS_URL);
        return publicStreamingService.connect();
    }

    @Override
    public Completable disconnect() {
        GlobalStreamingService service = publicStreamingService;
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

    public boolean isPrivateAlive() {
        return privateStreamingService != null && privateStreamingService.isSocketOpen();
    }

    @Override
    public void useCompressedMessages(boolean compressedMessages) {
        publicStreamingService.useCompressedMessages(compressedMessages);
    }
}