package com.orderbook.core.domain;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@RequiredArgsConstructor
public class SymbolBo {

    private boolean open;
    private long updateIntervalMs = 1000;
    private String baseTokenId;
    private String quoteTokenId;
    private BigDecimal platformTakerRate;
    private BigDecimal tickSize; //价格精度限制
    private BigDecimal stepSize; //数量精度限制
    private BigDecimal minSize; //最小下单数量
    private BigDecimal maxSize; //最大下单数量
    private BigDecimal minRate; //盘口的最小差异比例
    private BigDecimal maxRate; //盘口的最大差异比例
    private BigDecimal maxDelegateCount; //单笔最大的下单数量
    private String accountId;
    private String apiKey;
    private String secretKey;
    private String password;
    private Map<String, String> othersProps = new HashMap<>();

    public String getSymbolId() {
        return this.baseTokenId + "/" + this.quoteTokenId;
    }

    public String getSymbol() {
        return this.baseTokenId + this.quoteTokenId;
    }
}