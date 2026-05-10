package com.orderbook.core.backtest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BacktestResult {
    private String id;
    private String symbol;
    private String model;
    private long startTime;
    private long endTime;
    private int totalTicks;
    private BigDecimal initialCapital;
    private BigDecimal finalBalance;
    private BigDecimal totalReturn;
    private BigDecimal annualizedReturn;
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdown;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal winRate;
    private BigDecimal totalFees;
    private List<BacktestTrade> trades = new ArrayList<>();

    // Enhanced fields
    private List<BigDecimal> equityCurve = new ArrayList<>();
    private BigDecimal calmarRatio;
    private BigDecimal avgTradePnl;
    private BigDecimal profitFactor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public int getTotalTicks() { return totalTicks; }
    public void setTotalTicks(int totalTicks) { this.totalTicks = totalTicks; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }
    public BigDecimal getFinalBalance() { return finalBalance; }
    public void setFinalBalance(BigDecimal finalBalance) { this.finalBalance = finalBalance; }
    public BigDecimal getTotalReturn() { return totalReturn; }
    public void setTotalReturn(BigDecimal totalReturn) { this.totalReturn = totalReturn; }
    public BigDecimal getAnnualizedReturn() { return annualizedReturn; }
    public void setAnnualizedReturn(BigDecimal annualizedReturn) { this.annualizedReturn = annualizedReturn; }
    public BigDecimal getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }
    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public void setWinningTrades(int winningTrades) { this.winningTrades = winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }
    public BigDecimal getWinRate() { return winRate; }
    public void setWinRate(BigDecimal winRate) { this.winRate = winRate; }
    public BigDecimal getTotalFees() { return totalFees; }
    public void setTotalFees(BigDecimal totalFees) { this.totalFees = totalFees; }
    public List<BacktestTrade> getTrades() { return trades; }
    public void setTrades(List<BacktestTrade> trades) { this.trades = trades; }
    public List<BigDecimal> getEquityCurve() { return equityCurve; }
    public void setEquityCurve(List<BigDecimal> equityCurve) { this.equityCurve = equityCurve; }
    public BigDecimal getCalmarRatio() { return calmarRatio; }
    public void setCalmarRatio(BigDecimal calmarRatio) { this.calmarRatio = calmarRatio; }
    public BigDecimal getAvgTradePnl() { return avgTradePnl; }
    public void setAvgTradePnl(BigDecimal avgTradePnl) { this.avgTradePnl = avgTradePnl; }
    public BigDecimal getProfitFactor() { return profitFactor; }
    public void setProfitFactor(BigDecimal profitFactor) { this.profitFactor = profitFactor; }
}
