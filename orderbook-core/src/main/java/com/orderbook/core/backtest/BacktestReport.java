package com.orderbook.core.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Generates human-readable backtest reports from BacktestResult.
 */
public class BacktestReport {

    private static final String SEPARATOR = "═══════════════════════════════════════════════════";
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        DATE_FMT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Generate a plain-text summary report.
     */
    public String generateSummary(BacktestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(SEPARATOR).append("\n");
        sb.append("  BACKTEST REPORT\n");
        sb.append(SEPARATOR).append("\n\n");

        sb.append("  Symbol:         ").append(result.getSymbol()).append("\n");
        sb.append("  Model:          ").append(result.getModel()).append("\n");
        sb.append("  Period:         ").append(formatTime(result.getStartTime()))
                .append(" → ").append(formatTime(result.getEndTime())).append("\n");
        sb.append("  Ticks:          ").append(result.getTotalTicks()).append("\n\n");

        sb.append("  ── Performance ──\n");
        sb.append("  Initial Capital: ").append(formatCurrency(result.getInitialCapital())).append("\n");
        sb.append("  Final Equity:   ").append(formatCurrency(result.getFinalBalance())).append("\n");
        sb.append("  Total Return:   ").append(formatPct(result.getTotalReturn())).append("\n");
        sb.append("  Annualized Ret: ").append(formatPct(result.getAnnualizedReturn())).append("\n");
        sb.append("  Sharpe Ratio:   ").append(formatDecimal(result.getSharpeRatio())).append("\n");
        sb.append("  Calmar Ratio:   ").append(formatDecimal(result.getCalmarRatio())).append("\n");
        sb.append("  Max Drawdown:   ").append(formatPct(result.getMaxDrawdown())).append("\n\n");

        sb.append("  ── Trades ──\n");
        sb.append("  Total Trades:   ").append(result.getTotalTrades()).append("\n");
        sb.append("  Winning:        ").append(result.getWinningTrades()).append("\n");
        sb.append("  Losing:         ").append(result.getLosingTrades()).append("\n");
        sb.append("  Win Rate:       ").append(formatPct(result.getWinRate())).append("\n");
        sb.append("  Avg Trade PnL:  ").append(formatCurrency(result.getAvgTradePnl())).append("\n");
        sb.append("  Profit Factor:  ").append(formatDecimal(result.getProfitFactor())).append("\n");
        sb.append("  Total Fees:     ").append(formatCurrency(result.getTotalFees())).append("\n");

        sb.append("\n").append(SEPARATOR).append("\n");
        return sb.toString();
    }

    /**
     * Generate a detailed trade-by-trade report.
     */
    public String generateDetail(BacktestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(generateSummary(result));
        sb.append("\n  ── Trades ──\n");
        sb.append(String.format("  %-20s %-6s %-12s %-8s %-12s %s\n",
                "Time", "Side", "Price", "Qty", "Fee", "Realized PnL"));
        sb.append("  ").append("-".repeat(75)).append("\n");

        List<BacktestTrade> trades = result.getTrades();
        for (int i = Math.max(0, trades.size() - 50); i < trades.size(); i++) {
            BacktestTrade t = trades.get(i);
            sb.append(String.format("  %-20s %-6s %-12s %-8s %-12s %s\n",
                    formatTime(t.getTime()),
                    t.getSide(),
                    formatCurrency(t.getPrice()),
                    formatDecimal(t.getQuantity()),
                    formatCurrency(t.getFee()),
                    formatCurrency(t.getRealizedPnl())));
        }
        if (trades.size() > 50) {
            sb.append("  ... (").append(trades.size() - 50).append(" more trades omitted)\n");
        }
        sb.append("\n").append(SEPARATOR).append("\n");
        return sb.toString();
    }

    private String formatTime(long epochMs) {
        return DATE_FMT.format(new Date(epochMs));
    }

    private String formatCurrency(BigDecimal val) {
        if (val == null) return "N/A";
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return "$" + df.format(val.setScale(2, RoundingMode.HALF_UP));
    }

    private String formatPct(BigDecimal val) {
        if (val == null) return "N/A";
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(val.setScale(2, RoundingMode.HALF_UP)) + "%";
    }

    private String formatDecimal(BigDecimal val) {
        if (val == null) return "N/A";
        DecimalFormat df = new DecimalFormat("#,##0.0000");
        return df.format(val.setScale(4, RoundingMode.HALF_UP));
    }
}
