package com.orderbook.connector.stream.bybit;

import com.fasterxml.jackson.databind.JsonNode;
import com.orderbook.connector.stream.bybit.dto.BybitSubscribeMessage;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.Setter;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

public class BybitStreamingService extends JsonNettyStreamingService {
    private static final Logger LOG = LoggerFactory.getLogger(BybitStreamingService.class);
    public final String exchange_type;
    private final Observable<Long> pingPongSrc = Observable.interval(15, 15, java.util.concurrent.TimeUnit.SECONDS);
    private Disposable pingPongSubscription;
    private final ExchangeSpecification spec;

    @Setter
    private WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler = null;

    public BybitStreamingService(String apiUrl, ExchangeSpecification spec) {
        super(apiUrl);
        BybitCategory bybitCategory = (BybitCategory) spec.getExchangeSpecificParametersItem("Exchange_Type");
        this.exchange_type = bybitCategory.getValue();
        this.spec = spec;
    }

    @Override
    public Completable connect() {
        Completable conn = super.connect();
        return conn.andThen((CompletableSource) completable -> {
            pingPongDisconnectIfConnected();
            pingPongSubscription = pingPongSrc.subscribe(o -> this.sendMessage("{\"op\":\"ping\"}"), completable::onError, completable::onComplete);
        });
    }

    @Override
    protected String getChannelNameFromMessage(JsonNode message) {
        if (message.has("topic")) {
            return message.get("topic").asText();
        }
        return "";
    }

    @Override
    public String getSubscribeMessage(String channelName, Object... args) throws IOException {
        LOG.info("getSubscribeMessage {}", channelName);
        return objectMapper.writeValueAsString(new BybitSubscribeMessage("subscribe", Collections.singletonList(channelName)));
    }

    @Override
    public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
        LOG.info("getUnsubscribeMessage {}", channelName);
        return objectMapper.writeValueAsString(new BybitSubscribeMessage("unsubscribe", Collections.singletonList(channelName)));
    }

    @Override
    public void messageHandler(String message) {
        LOG.debug("Received message: {}", message);
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(message);
        } catch (IOException e) {
            LOG.error("Error parsing incoming message to JSON: {}", message);
            return;
        }

        String op = "";
        boolean success = false;
        if (jsonNode.has("op")) {
            op = jsonNode.get("op").asText();
        }
        if (jsonNode.has("success")) {
            success = jsonNode.get("success").asBoolean();
        }

        if (success) {
            switch (op) {
                case "subscribe":
                case "unsubscribe":
                    break;
            }
            return;
        } else {
            if (op.equals("ping") || op.equals("pong")) {
                LOG.debug("Received PONG message: {}", message);
                return;
            }
            handleMessage(jsonNode);
        }
    }

    public void pingPongDisconnectIfConnected() {
        if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
            pingPongSubscription.dispose();
        }
    }

    @Override
    protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
        return null;
    }

    @Override
    protected WebSocketClientHandler getWebSocketClientHandler(WebSocketClientHandshaker handshake, WebSocketClientHandler.WebSocketMessageHandler handler) {
        LOG.info("Registering BybitWebSocketClientHandler");
        return new BybitWebSocketClientHandler(handshake, handler);
    }

    class BybitWebSocketClientHandler extends NettyWebSocketClientHandler {
        public BybitWebSocketClientHandler(WebSocketClientHandshaker handshake, WebSocketMessageHandler handler) {
            super(handshake, handler);
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