package com.orderbook.connector.stream.global;

import com.orderbook.connector.common.dto.BitgetEventNotification;
import com.orderbook.connector.common.dto.BitgetLoginRequest;
import com.orderbook.connector.common.dto.BitgetWsNotification;
import com.orderbook.connector.common.dto.Operation;
import com.orderbook.connector.stream.global.config.Config;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class GlobalPrivateStreamingService extends GlobalStreamingService {

    private final String apiKey;
    private final String apiSecret;
    private final String apiPassword;
    private AtomicBoolean isLogin = new AtomicBoolean(false);

    public GlobalPrivateStreamingService(String apiUri, String apiKey, String apiSecret, String apiPassword) {
        super(apiUri);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiPassword = apiPassword;
    }

    @SneakyThrows
    public void sendLoginMessage() {
        Instant timestamp = Instant.now(Config.getInstance().getClock());
        BitgetLoginRequest bitgetLoginRequest =
                BitgetLoginRequest.builder()
                        .operation(Operation.LOGIN)
                        .payload(
                                BitgetLoginRequest.LoginPayload.builder()
                                        .apiKey(apiKey)
                                        .passphrase(apiPassword)
                                        .timestamp(timestamp)
                                        .signature(GlobalStreamingAuthHelper.sign(timestamp, apiSecret))
                                        .build()
                        )
                        .build();
        sendMessage(objectMapper.writeValueAsString(bitgetLoginRequest));
    }

    @Override
    public void resubscribeChannels() {
        isLogin.set(false);
        sendLoginMessage();
        super.resubscribeChannels();
    }

    @Override
    protected void handleMessage(BitgetWsNotification message) {
        // subscribe to channels after successful login confirmation
        if (message instanceof BitgetEventNotification) {
            BitgetEventNotification eventNotification = (BitgetEventNotification) message;
            if (eventNotification.getEvent() == BitgetEventNotification.Event.LOGIN && eventNotification.getCode() == 0) {
                log.info("GlobalPrivateStreamingService received login event resubscribe");
                isLogin.set(true);
                return;
            }
        }
        super.handleMessage(message);
    }

    public boolean isLogin() {
        return isLogin.get();
    }
}