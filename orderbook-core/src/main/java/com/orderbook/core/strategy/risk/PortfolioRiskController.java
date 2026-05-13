package com.orderbook.core.strategy.risk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for portfolio-level risk metrics.
 * 用于获取投资组合级别风险指标的 REST API。
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioRiskController {

    private final PortfolioRiskManager portfolioRiskManager;

    public PortfolioRiskController(PortfolioRiskManager portfolioRiskManager) {
        this.portfolioRiskManager = portfolioRiskManager;
    }

    @GetMapping("/value")
    public Map<String, Object> getPortfolioValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPortfolioValue", portfolioRiskManager.getTotalPortfolioValue());
        result.put("peakPortfolioValue", portfolioRiskManager.getPeakPortfolioValue());
        result.put("drawdownPct", portfolioRiskManager.getDrawdownPct());
        return result;
    }

    @GetMapping("/var")
    public Map<String, Object> getPortfolioVaR() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("var95", portfolioRiskManager.getPortfolioVaR95());
        return result;
    }

    @GetMapping("/sharpe")
    public Map<String, Object> getPortfolioSharpe() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sharpeRatio", portfolioRiskManager.getPortfolioSharpeRatio());
        return result;
    }

    @GetMapping("/concentration")
    public Map<String, Double> getConcentration() {
        return portfolioRiskManager.getAllConcentrations();
    }

    @GetMapping("/correlation")
    public Map<String, Map<String, Double>> getCorrelation() {
        return portfolioRiskManager.getCorrelationMatrix();
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPortfolioValue", portfolioRiskManager.getTotalPortfolioValue());
        result.put("peakPortfolioValue", portfolioRiskManager.getPeakPortfolioValue());
        result.put("drawdownPct", portfolioRiskManager.getDrawdownPct());
        result.put("var95", portfolioRiskManager.getPortfolioVaR95());
        result.put("sharpeRatio", portfolioRiskManager.getPortfolioSharpeRatio());
        result.put("concentration", portfolioRiskManager.getAllConcentrations());
        return result;
    }
}
