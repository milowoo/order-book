package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BitgetAccountNotification extends BitgetWsNotification<BitgetAccountNotification.AccountData> {

    @Data
    @Builder
    @Jacksonized
    public static class AccountData {
        // 币种名称
        @JsonProperty("coin")
        private String coin;

        // 币种可用资产
        @JsonProperty("available")
        private String available;

        // 冻结资产数量，通常是下单时冻结
        @JsonProperty("frozen")
        private String frozen;

        // 锁仓资产数量，成为法币商家等的锁仓
        @JsonProperty("locked")
        private String locked;

        // 受限可用，用于现货跟单场景
        @JsonProperty("limitAvailable")
        private String limitAvailable;

        // 更新时间，Unix时间戳的毫秒数格式，如 1597026383085
        @JsonProperty("uTime")
        private String uTime;
    }
}