package com.orderbook.connector.stream.bitget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderbook.connector.stream.bitget.config.Config;
import com.orderbook.connector.common.dto.Action;
import com.orderbook.connector.common.dto.BitgetChannel;
import com.orderbook.connector.common.dto.BitgetWsRequest;
import com.orderbook.connector.common.dto.Operation;
import com.orderbook.connector.common.dto.BitgetEventNotification;
import com.orderbook.connector.common.dto.BitgetWsNotification;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BitgetStreamingService extends NettyStreamingService<BitgetWsNotification> {

    protected final ObjectMapper objectMapper = Config.getInstance().getObjectMapper();
    private Disposable pingPongSubscription;
    private final Observable<Long> pingPongSrc = Observable.interval(15, 20, TimeUnit.SECONDS);

    public BitgetStreamingService(String apiUri) {
        super(apiUri, Integer.MAX_VALUE);
    }

    @Override
    protected String getChannelNameFromMessage(BitgetWsNotification message) {
        return BitgetStreamingAdapters.toSubscriptionId(message.getChannel());
    }

    // Params: channelName - ignored
    // args - [ChannelType, MarketType, Instrument / null]
    // Returns: message to be sent for subscribing
    // See Also: info.bitrich.xchangestream.bitget.BitgetStreamingAdapters.toSubscriptionId
    @Override
    public String getSubscribeMessage(String channelName, Object... args) throws IOException {
        BitgetChannel bitgetChannel = BitgetStreamingAdapters.toBitgetChannel(args);

        BitgetWsRequest request =
                BitgetWsRequest.builder().operation(Operation.SUBSCRIBE).channel(bitgetChannel).build();
        String reqJsonStr = objectMapper.writeValueAsString(request);
        // 处理合约账户频道特殊参数
        if (BitgetChannel.ChannelType.ACCOUNT.equals(bitgetChannel.getChannelType())) {
            reqJsonStr = reqJsonStr.replace("instId", "coin");
        }
        return reqJsonStr;
    }

    // Params: channelName - ignored
    // args - array with [MarketType, Instrument]
    // Returns: message to be sent for unsubscribing
    // See Also: info.bitrich.xchangestream.bitget.BitgetStreamingAdapters.toSubscriptionId
    @Override
    public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
        BitgetChannel bitgetChannel = BitgetStreamingAdapters.toBitgetChannel(args);

        BitgetWsRequest request =
                BitgetWsRequest.builder().operation(Operation.UNSUBSCRIBE).channel(bitgetChannel).build();
        return objectMapper.writeValueAsString(request);
    }

    @Override
    protected void handleMessage(BitgetWsNotification message) {
        log.debug("Processing {}", message.toString());
        // no special processing of event messages
        if (message instanceof BitgetEventNotification) {
            return;
        }
        super.handleMessage(message);
    }

    @Override
    protected void handleChannelMessage(String channel, BitgetWsNotification message) {
        List<Action> list = Arrays.asList(Action.values());
        if (message.getAction() == null || !list.contains(message.getAction())) {
            return;
        }
        super.handleChannelMessage(channel, message);
    }

    // Params: channelName - name of channel
    // args - array with [MarketType, Instrument]
    // Returns: subscription id in form of marketType_channelName_instrument1_instrumentX
    @Override
    public String getSubscriptionUniqueId(String channelName, Object... args) {
        BitgetChannel bitgetChannel = BitgetStreamingAdapters.toBitgetChannel(args);
        return BitgetStreamingAdapters.toSubscriptionId(bitgetChannel);
    }

    @Override
    public void messageHandler(String message) {
        log.debug("Received message: {}", message);
        if (message.contains("pong")) {
            return;
        }

        BitgetWsNotification bitgetWsNotification;

        // Parse incoming message to JSON
        try {
            JsonNode jsonNode = objectMapper.readTree(message);

            // try to parse event
            if (jsonNode.has("event")) {
                ((ObjectNode) jsonNode).put("messageType", "event");
            }
            // copy nested value of arg.channel to the root of json to detect deserialization type
            else if (jsonNode.has("arg") && jsonNode.get("arg").has("channel")) {
                ((ObjectNode) jsonNode).put("messageType", jsonNode.get("arg").get("channel").asText());
            }
            // 处理账户频道特殊参数
            if (jsonNode.has("arg") && jsonNode.get("arg").has("coin")) {
                ((ObjectNode) jsonNode).put("instId", jsonNode.get("arg").get("coin").asText());
            }

            bitgetWsNotification = objectMapper.treeToValue(jsonNode, BitgetWsNotification.class);
        } catch (IOException e) {
            log.error("Error parsing incoming message to JSON: {}", message);
            log.error(e.getMessage(), e);
            return;
        }

        // if payload has several items process each item as a separate notification
        if (bitgetWsNotification.getPayloadItems() != null
                && bitgetWsNotification.getPayloadItems().size() > 1) {
            for (Object payloadItem : bitgetWsNotification.getPayloadItems()) {
                handleMessage(bitgetWsNotification.toBuilder().payloadItem(payloadItem).build());
            }
        } else {
            handleMessage(bitgetWsNotification);
        }
    }

    @Override
    public Completable connect() {
        Completable connect = super.connect();
        return connect.andThen((CompletableSource) completable -> {
            pingPongDisconnectIfConnected();
            pingPongSubscription =
                    pingPongSrc.subscribe((Long o) -> this.sendMessage("ping"));
            completable.onComplete();
        });
    }

    public void pingPongDisconnectIfConnected() {
        if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
            pingPongSubscription.dispose();
        }
    }
}