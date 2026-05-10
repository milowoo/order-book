package com.orderbook.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExchangeInfo {
    @JsonProperty("api_key")
    private String apiKey;
    @JsonProperty("secret_key")
    private String secretKey;
    @JsonProperty("api_url")
    private String apiUrl = "";
    @JsonProperty("host")
    private String host = "";
    @JsonProperty("use_sandbox")
    private Boolean useSandbox = false;
    @JsonProperty("password")
    private String password = "";
    @JsonProperty("is_use_demo")
    private Boolean isUseDemo = false;
    @JsonProperty("is_use_testnet")
    private Boolean isUseTestnet = false;
    @JsonProperty("stream_public_use")
    private Boolean streamPublicUse = true;
    @JsonProperty("stream_private_use")
    private Boolean streamPrivateUse = true;
}