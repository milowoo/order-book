package com.orderbook.connector.stream.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.collect.Sets;
import com.orderbook.connector.stream.binance.dto.BinanceRawTrade;
import com.orderbook.connector.stream.binance.dto.BinanceWebSocketSubscriptionMessage;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientCompressionAllowClientNoContextAndServerNoContextHandler;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.reactivex.rxjava3.core.Observable;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.binance.dto.marketdata.BinanceTicker24h;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BinanceStreamingService extends JsonNettyStreamingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceStreamingService.class);
    private static final String RESULT = "result";
    private static final String IDENTIFIER = "id";

    private final ProductSubscription productSubscription;
    private final KlineSubscription klineSubscription;
    private boolean isLiveSubscriptionEnabled = false;
    private WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler = null;
    private final Map<Integer, BinanceWebSocketSubscriptionMessage> liveSubscriptionMessage = new ConcurrentHashMap<>();

    public BinanceStreamingService(
            String baseUri,
            ProductSubscription productSubscription,
            KlineSubscription klineSubscription) {
        super(baseUri, Integer.MAX_VALUE);
        this.productSubscription = productSubscription;
        this.klineSubscription = klineSubscription;
    }

    public BinanceStreamingService(
            String baseUri,
            ProductSubscription productSubscription,
            KlineSubscription klineSubscription,
            int maxFramePayloadLength,
            Duration connectionTimeout,
            Duration retryDuration,
            int idleTimeoutSeconds) {
        super(baseUri, maxFramePayloadLength, connectionTimeout, retryDuration, idleTimeoutSeconds);
        this.productSubscription = productSubscription;
        this.klineSubscription = klineSubscription;
    }

    @Override
    protected String getChannelNameFromMessage(JsonNode message) {
        return message.get("stream").asText();
    }

    @Override
    protected void handleMessage(JsonNode message) {
        final JsonNode result = message.get(RESULT);
        final JsonNode identifier = message.get(IDENTIFIER);

        // If there is a result field (with null as value) and there is an id field with value != null,
        // it's the response from Live Subscribing/Unsubscribing stream
        if (result instanceof NullNode && identifier != null) {
            try {
                final Integer id = Integer.parseInt(identifier.asText());
                final BinanceWebSocketSubscriptionMessage subscriptionMessage = this.liveSubscriptionMessage.get(id);
                if (subscriptionMessage != null) {
                    final String streamName = subscriptionMessage.getParams().get(0);
                    switch (subscriptionMessage.getMethod()) {
                        case SUBSCRIBE:
                            LOGGER.info("Stream {} has been successfully subscribed", streamName);
                            break;
                        case UNSUBSCRIBE:
                            LOGGER.info("Stream {} has been successfully unsubscribed", streamName);
                            break;
                    }
                    this.liveSubscriptionMessage.remove(id);
                }
            } catch (final NumberFormatException exception) {
                // Nothing to do
            }
        } else {
            super.handleMessage(message);
        }
    }

    @Override
    public void resubscribeChannels() {
        // Nothing to do, Subscriptions are made upon connection - no messages to send
    }

    @Override
    public String getSubscribeMessage(String channelName, Object... args) throws IOException {
        if (isLiveSubscriptionEnabled) {
            updateConnectionUri(channelName, BinanceWebSocketSubscriptionMessage.MethodType.SUBSCRIBE);
            return generateMessage(BinanceWebSocketSubscriptionMessage.MethodType.SUBSCRIBE, channelName);
        }
        // No subscribe message required if Live Subscription is disabled
        return null;
    }

    @Override
    public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
        if (isLiveSubscriptionEnabled) {
            updateConnectionUri(channelName, BinanceWebSocketSubscriptionMessage.MethodType.UNSUBSCRIBE);
            return generateMessage(BinanceWebSocketSubscriptionMessage.MethodType.UNSUBSCRIBE, channelName);
        }
        // No unsubscribe message required if Live Subscription is disabled
        return null;
    }

    private void updateConnectionUri(String channelName, final BinanceWebSocketSubscriptionMessage.MethodType methodType) {
        final String baseConnectionUrl = uri.toString().substring(0, uri.toString().indexOf("=") + 1);
        final String subscribedChannels = uri.toString().substring(uri.toString().indexOf("=") + 1);
        final Set<String> channels =
                subscribedChannels.isEmpty()
                        ? new HashSet<>()
                        : Sets.newHashSet(subscribedChannels.split("/"));
        switch (methodType) {
            case SUBSCRIBE:
                channels.add(channelName);
                break;
            case UNSUBSCRIBE:
                channels.remove(channelName);
                break;
        }
        final String newConnectionUrl = baseConnectionUrl + String.join("/", channels);
        try {
            uri = new URI(newConnectionUrl);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Error parsing URI " + newConnectionUrl, exception);
        }
    }

    private String generateMessage(final BinanceWebSocketSubscriptionMessage.MethodType methodType, final String channelName)
            throws IOException {
        final int identifier = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        final BinanceWebSocketSubscriptionMessage message =
                new BinanceWebSocketSubscriptionMessage(methodType, channelName, identifier);
        this.liveSubscriptionMessage.put(identifier, message);
        return objectMapper.writeValueAsString(message);
    }

    @Override
    public void sendMessage(String message) {
        if (isLiveSubscriptionEnabled) {
            super.sendMessage(message);
        }
        // If Live Subscription is disabled, Subscriptions are made upon connection - no messages are sent.
    }

    @Override
    protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
        return WebSocketClientCompressionAllowClientNoContextAndServerNoContextHandler.INSTANCE;
    }

    public ProductSubscription getProductSubscription() {
        return productSubscription;
    }

    public KlineSubscription getKlineSubscription() {
        return klineSubscription;
    }

    public Observable<BinanceRawTrade> getRawTrades(Instrument instrument) {
        String channelName = getPrefix(instrument) + "@" + BinanceSubscriptionType.TRADE.getType();
        return subscribeChannel(channelName)
                .map(jsonNode -> objectMapper.treeToValue(jsonNode.get("data"), BinanceRawTrade.class));
    }

    public Observable<BinanceTicker24h> getRawTicker(Instrument instrument) {
        String channelName = getPrefix(instrument) + "@" + BinanceSubscriptionType.TICKER.getType();
        return subscribeChannel(channelName)
                .map(jsonNode -> objectMapper.treeToValue(jsonNode.get("data"), BinanceTicker24h.class));
    }

    private static String getPrefix(Instrument pair) {
        String prefix = String.join("", pair.toString().split("/")).toLowerCase();
        if (pair instanceof org.knowm.xchange.derivative.FuturesContract) {
            prefix = String.join("", ((org.knowm.xchange.derivative.FuturesContract) pair).getCurrencyPair().toString().split("/")).toLowerCase();
        }
        return prefix;
    }

    public void enableLiveSubscription() {
        isLiveSubscriptionEnabled = true;
    }

    public void disableLiveSubscription() {
        isLiveSubscriptionEnabled = false;
    }

    public boolean isLiveSubscriptionEnabled() {
        return isLiveSubscriptionEnabled;
    }

    public void unsubscribeChannel(final String channelId) {
        if (channels.remove(channelId) != null) {
            try {
                sendMessage(getUnsubscribeMessage(channelId));
            } catch (IOException e) {
                LOGGER.debug("Failed to unsubscribe channel: {} {}", channelId, e.toString());
            } catch (Exception e) {
                LOGGER.warn("Failed to unsubscribe channel: {}", channelId, e);
            }
        }
    }

    @Override
    protected WebSocketClientHandler getWebSocketClientHandler(
            WebSocketClientHandshaker handshaker, WebSocketClientHandler.WebSocketMessageHandler handler) {
        LOGGER.info("Registering BinanceWebSocketClientHandler");
        return new BinanceWebSocketClientHandler(handshaker, handler);
    }

    public void setChannelInactiveHandler(WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler) {
        this.channelInactiveHandler = channelInactiveHandler;
    }

    class BinanceWebSocketClientHandler extends NettyWebSocketClientHandler {
        public BinanceWebSocketClientHandler(
                WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
            super(handshaker, handler);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            super.channelInactive(ctx);
            if (channelInactiveHandler != null) {
                channelInactiveHandler.onMessage("WebSocket Client disconnected!");
            }
        }
    }
}