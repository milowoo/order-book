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

    // Enhanced fields
    private boolean riskEnabled = true;
    private boolean alphaEnabled = false;
    private String mlModelName;
    private double mlModelWeight = 0.3;
    private String exchange = "BYBIT";
    private BigDecimal maxDrawdownPercent = new BigDecimal("10.0");
    private int circuitBreakerThreshold = 10;
    private long circuitBreakerCooldownMs = 60000;

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
    public boolean isRiskEnabled() { return riskEnabled; }
    public void setRiskEnabled(boolean riskEnabled) { this.riskEnabled = riskEnabled; }
    public boolean isAlphaEnabled() { return alphaEnabled; }
    public void setAlphaEnabled(boolean alphaEnabled) { this.alphaEnabled = alphaEnabled; }
    public String getMlModelName() { return mlModelName; }
    public void setMlModelName(String mlModelName) { this.mlModelName = mlModelName; }
    public double getMlModelWeight() { return mlModelWeight; }
    public void setMlModelWeight(double mlModelWeight) { this.mlModelWeight = mlModelWeight; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public BigDecimal getMaxDrawdownPercent() { return maxDrawdownPercent; }
    public void setMaxDrawdownPercent(BigDecimal maxDrawdownPercent) { this.maxDrawdownPercent = maxDrawdownPercent; }
    public int getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) { this.circuitBreakerThreshold = circuitBreakerThreshold; }
    public long getCircuitBreakerCooldownMs() { return circuitBreakerCooldownMs; }
    public void setCircuitBreakerCooldownMs(long circuitBreakerCooldownMs) { this.circuitBreakerCooldownMs = circuitBreakerCooldownMs; }
}
