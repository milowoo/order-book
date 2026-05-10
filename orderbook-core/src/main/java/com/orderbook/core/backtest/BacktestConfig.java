package com.orderbook.core.backtest;

import java.math.BigDecimal;
import java.util.Map;

public class BacktestConfig {
    private String symbol;
    private long startTime;
    private long endTime;
    private String model = "hybrid";
    private BigDecimal initialCapital = new BigDecimal("1000");
    private BigDecimal makerFeeRate = new BigDecimal("0.001");
    private BigDecimal takerFeeRate = new BigDecimal("0.001");
    private Map<String, Object> modelParams;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }
    public BigDecimal getMakerFeeRate() { return makerFeeRate; }
    public void setMakerFeeRate(BigDecimal makerFeeRate) { this.makerFeeRate = makerFeeRate; }
    public BigDecimal getTakerFeeRate() { return takerFeeRate; }
    public void setTakerFeeRate(BigDecimal takerFeeRate) { this.takerFeeRate = takerFeeRate; }
    public Map<String, Object> getModelParams() { return modelParams; }
    public void setModelParams(Map<String, Object> modelParams) { this.modelParams = modelParams; }
}
