package com.orderbook.connector.stream.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitBalanceChangesResponse {
    private String id;
    private String topic;

    @JsonProperty("creationTime")
    private Date timestamp;

    @JsonProperty("data")
    private List<AccountData> data;

    public List<BalanceCoin> getCoins() {
        if (data != null && !data.isEmpty()) {
            return data.get(0).getCoins();
        }
        return List.of();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountData {
        @JsonProperty("coin")
        private List<BalanceCoin> coin;

        public List<BalanceCoin> getCoins() {
            return coin;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BalanceCoin {
        private String coin;
        private BigDecimal equity;
        private BigDecimal usdValue;
        private BigDecimal walletBalance;
        private BigDecimal availableToWithdraw;
        private BigDecimal availableToBorrow;
        private BigDecimal borrowAmount;
        private BigDecimal accruedInterest;
        private BigDecimal totalOrderIM;
        private BigDecimal totalPositionIM;
        private BigDecimal totalPositionMM;
        private BigDecimal unrealisedPnl;
        private BigDecimal cumRealisedPnl;
        private BigDecimal bonus;
        private boolean collateralSwitch;
        private boolean marginCollateral;
        private BigDecimal locked;
        private BigDecimal spotHedgingQty;
    }
}