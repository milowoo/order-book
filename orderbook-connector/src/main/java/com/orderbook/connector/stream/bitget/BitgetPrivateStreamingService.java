package com.orderbook.connector.stream.bitget;

import com.orderbook.connector.stream.bitget.config.Config;
import com.orderbook.connector.common.dto.Operation;
import com.orderbook.connector.common.dto.BitgetLoginRequest;
import com.orderbook.connector.common.dto.BitgetEventNotification;
import com.orderbook.connector.common.dto.BitgetWsNotification;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.Map.Entry;

@Slf4j
public class BitgetPrivateStreamingService extends BitgetStreamingService {

    private final String apiKey;
    private final String apiSecret;
    private final String apiPassword;

    public BitgetPrivateStreamingService(String apiUri, String apiKey, String apiSecret, String apiPassword) {
        super(apiUri);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiPassword = apiPassword;
    }

    // Sends login message right after connecting
    @Override
    public void resubscribeChannels() {
        sendLoginMessage();
    }

    public void resubscribeChannelsAfterLogin() {
        for (Entry<String, Subscription> entry : channels.entrySet()) {
            try {
                Subscription subscription = entry.getValue();
                sendMessage(getSubscribeMessage(subscription.getChannelName(), subscription.getArgs()));
            } catch (IOException e) {
                log.error("Failed to reconnect channel: {}", entry.getKey());
            }
        }
    }

    @SneakyThrows
    private void sendLoginMessage() {
        Instant timestamp = Instant.now(Config.getInstance().getClock());
        BitgetLoginRequest bitgetLoginRequest =
                BitgetLoginRequest.builder()
                        .operation(Operation.LOGIN)
                        .payload(
                                BitgetLoginRequest.LoginPayload.builder()
                                        .apiKey(apiKey)
                                        .passphrase(apiPassword)
                                        .timestamp(timestamp)
                                        .signature(BitgetStreamingAuthHelper.sign(timestamp, apiSecret))
                                        .build()
                        )
                        .build();
        sendMessage(objectMapper.writeValueAsString(bitgetLoginRequest));
    }

    @Override
    protected void handleMessage(BitgetWsNotification message) {
        // subscribe to channels after successful login confirmation
        if (message instanceof BitgetEventNotification) {
            BitgetEventNotification eventNotification = (BitgetEventNotification) message;
            if (eventNotification.getEvent() == BitgetEventNotification.Event.LOGIN && eventNotification.getCode() == 0) {
                resubscribeChannelsAfterLogin();
                return;
            }
        }
        super.handleMessage(message);
    }
}