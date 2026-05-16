# 回测系统设计文档

## 概述

回测系统是量化交易策略开发的核心基础设施，本质上是一个"时光机 + 模拟交易账户"——在历史数据上精准复现策略的执行过程，检验策略在历史行情中的表现。

本系统采用 **快照驱动 + 事件循环** 的架构：按时间顺序逐个回放订单簿快照，在每个时间点上执行价差计算、信号合成、风控检查、成交模拟和 PnL 核算，最终输出绩效评估报告。

```
┌────────────────────────────────────────────────────────────┐
│                     BacktestController                       │
│  POST /run | POST /run-csv | GET /results | DELETE /results │
└──────────────────┬─────────────────────────────────────────┘
                   │
┌──────────────────▼─────────────────────────────────────────┐
│                      BacktestService                        │
│           结果缓存 (ConcurrentHashMap) + 异步执行             │
└──────────────────┬─────────────────────────────────────────┘
                   │
┌──────────────────▼─────────────────────────────────────────┐
│                      BacktestEngine                         │
│                   simulate(config, snapshots)                │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐│
│  │ 数据加载  │→ │ 策略计算 │→ │ 成交模拟 │→ │ PnL & 指标   ││
│  │ load/DSS │  │ spread + │  │ fill +   │  │ Sharpe/Calmar││
│  │ CSV parse│  │ alpha    │  │ risk     │  │ equityCurve  ││
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘│
└──────────────────┬─────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
  数据库 (TiDB)          CSV 文件
  order_book_snapshot   snapshots_full.csv
                        snapshots_price_only.csv
```

---

## 一、数据层（基础）

数据是回测的基石。本系统支持两种数据来源：数据库（TiDB）和 CSV 文件。

### 1.1 数据库模式

订单簿快照存储在 `order_book_snapshot` 表，由 `OrderBookSnapshotEntity` 映射：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `symbol` | VARCHAR(32) | 交易对，如 BTCUSDT |
| `exchange` | VARCHAR(32) | 交易所标识，如 BYBIT、BINANCE |
| `bids` | JSON / TEXT | 买单盘口 JSON 数组，格式 `[[price, qty], ...]` |
| `asks` | JSON / TEXT | 卖单盘口 JSON 数组，格式 `[[price, qty], ...]` |
| `bid_count` | INT | 买单档位数 |
| `ask_count` | INT | 卖单档位数 |
| `snapshot_time` | BIGINT | 快照时间戳（毫秒） |
| `created_at` | BIGINT | 记录创建时间 |

> 数据库模式（schema.sql）中还包含 `trade_record`（成交记录）、`train_dataset`（ML 训练数据）、`model_version`（模型版本管理）等辅助表。

### 1.2 数据加载流程

**数据库加载**（`BacktestEngine.loadSnapshots()`）：

```
OrderBookSnapshotMapper.selectList()
  └─ LambdaQueryWrapper
       ├─ eq(symbol)
       ├─ ge(snapshotTime, startTime)
       ├─ le(snapshotTime, endTime)
       └─ orderByAsc(snapshotTime)
             │
             ▼
  List<OrderBookSnapshotEntity>
             │
   解析 JSON bids/asks (fastjson)
             │
             ▼
  List<BacktestSnapshot>  ← 领域对象
```

`BacktestSnapshot` 是不可变领域对象，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `time` | long | 快照时间戳 |
| `bestBid` | BigDecimal | 最优买价 |
| `bestAsk` | BigDecimal | 最优卖价 |
| `midPrice` | BigDecimal | 中间价 `(bestBid + bestAsk) / 2` |
| `bids` | List\<PriceLevel\> | 买单盘口（已按价格降序排列） |
| `asks` | List\<PriceLevel\> | 卖单盘口（已按价格升序排列） |
| `exchange` | String | 交易所来源 |

`PriceLevel` 为 `(price, quantity)` 值对象，表示订单簿某一档位的价格和数量。

**CSV 加载**（`CsvSnapshotLoader.loadCsv()`）：

支持两种 CSV 格式，自动从表头行检测：

**完整订单簿格式**（包含盘口深度数据）：
```csv
timestamp_ms,best_bid,best_ask,bid_price_0,bid_qty_0,bid_price_1,bid_qty_1,...,ask_price_0,ask_qty_0,ask_price_1,ask_qty_1,...
1700000000000,49990.0,50010.0,49990.0,1.5,49980.0,2.0,...,50010.0,1.0,50020.0,1.5,...
```

**纯价格格式**（仅中间价，适用于快速验证）：
```csv
timestamp_ms,mid_price
1700000000000,50005.0
1700010000000,50010.0
```

两种加载方式统一进入 `BacktestEngine.simulate()`，后续流程完全一致。

### 1.3 数据质量保障

- **JSON 解析容错**：`loadSnapshots()` 中每条记录有独立 try-catch，解析失败的快照被跳过并记录 debug 日志，不影响其他快照
- **无效中间价过滤**：`simulate()` 循环内跳过 `midPrice == null || midPrice <= 0` 的快照
- **CSV 格式自动检测**：根据表头列数区分完整格式和纯价格格式，无需用户指定

---

## 二、策略层（大脑）

策略层是回测的"交易决策中心"。本系统的策略层由 **价差计算器** 和 **Alpha 信号** 两部分组成。

### 2.1 价差模型（Spread Models）

系统支持 4 种可配置的价差模型，通过 `SpreadCalculator` 接口统一抽象：

```java
public interface SpreadCalculator {
    BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo);
    String getName();
}
```

`calculateOffset()` 返回一个**正的价格偏移量**，用于计算挂单价格：

```
bidPrice = midPrice - bidOffset    ← 买单挂单价（低于中间价）
askPrice = midPrice + askOffset    ← 卖单挂单价（高于中间价）
```

#### 2.1.1 固定价差模型（ConstantSpreadCalculator）

最简单的模型，返回固定的 `baseOffsetTicks × tickSize` 偏移量，买卖对称：

```java
BigDecimal offset = baseOffsetTicks.multiply(symbolBo.getTickSize());
```

适用于币种选择初期的简单做市策略测试。

#### 2.1.2 库存依赖模型（InventoryBasedSpreadCalculator）

根据当前净持仓动态调整价差，是不对称价差策略的核心：

```
positionRatio = clamp((currentPosition - targetPosition) / maxPosition, -1, 1)

bidOffset  = base × (1 + skewFactor × positionRatio)  ← 做多时扩大，抑制买入
askOffset  = base × (1 - skewFactor × positionRatio)  ← 做多时缩小，鼓励卖出
```

当 Alpha 信号启用时，目标持仓会根据 Alpha 信号动态调整：
```
adjustedTarget = baseTarget + alpha × maxAlphaPositionAdjustment
```

数据来源：`AccountStore.getAccount(exchange)` 获取当前持仓。

#### 2.1.3 风险补偿模型（RiskAdjustedSpreadCalculator）

高波动时自动扩大价差，降低风险敞口：

```
multiplier = max(1.0, 1 + volCoeff × volatility)
bidOffset = askOffset = base × multiplier
```

数据来源：`VolatilityTracker.getVolatility()` 返回滚动变异系数（CV = stddev / mean），窗口大小可配置（默认 20 tick）。

#### 2.1.4 混合模型（HybridSpreadCalculator）

加权组合多个计算器：

```
offset = Σ(offset_i × weight_i) / Σ(weight_i)
```

默认权重组合：Constant 0.3 + Inventory 0.4 + Risk 0.3。

低波动时以库存管理为主，高波动时自动切换为风险规避。

### 2.2 Alpha 信号

Alpha 信号通过分析市场微观结构生成短期方向性判断，动态调整库存目标。

#### 2.2.1 回测内的 Alpha 计算

回测引擎在 `simulate()` 内部直接计算 Alpha，不依赖外部服务：

```java
// 计算 compositeAlpha (if alphaEnabled)
private BigDecimal computeCompositeAlpha(...) {
    // 1. 订单流不平衡: (bidVol - askVol) / (bidVol + askVol)
    double orderFlowAlpha = ...;

    // 2. 动量: tanh(ROC × 10) 映射到 [-1, 1]
    double momentumAlpha = ...;

    // 3. 加权合成
    composite = 0.5 × orderFlowAlpha + 0.5 × momentumAlpha;
    return clamp(composite, -1, 1);
}
```

回测中的 Alpha 计算与生产环境共享相同的核心逻辑：

| 信号 | 回测实现 | 生产实现 | 数据源 |
|------|---------|---------|--------|
| 订单流不平衡 | 根据快照 bid/ask 深度计算 | OrderFlowImbalanceAlpha | 快照/OrderBookStore |
| 动量 | 根据 VolatilityTracker 历史价格计算 | MomentumAlpha | VolatilityTracker |
| ML 信号 | 通过 MLModelRegistry 加载模型 | MLAlphaSignal | model_version 表 |
| 合成 | 回测内加权计算 | CompositeAlpha / AlphaAggregator | — |

#### 2.2.2 ML 模型集成

系统通过统一的 `MLModel` 接口支持两种机器学习模型：

| 模型类型 | 实现类 | 训练算法 | 推理方式 | 适用场景 |
|---------|--------|---------|---------|---------|
| 随机森林 | `RandomForestModel` | `RandomForestTrainer` (CART) | 纯 Java 决策树集成 | 内建训练，无需外部依赖 |
| XGBoost | `XGBoostModel` | `XGBoostTrainer` (xgboost4j) | XGBoost4j Booster | 高精度梯度提升 |

系统根据 `MLConfig.modelType` 字段（`"random_forest"` / `"xgboost"`）自动选择模型类型。

**随机森林模型**（内建实现）：
```
MLModelRegistry.getModel(symbol)
  └─ 查询 model_version 表，获取 active 状态的模型
       └─ deserialize JSON → RandomForestModel (纯 Java 决策树)
              └─ predict(features) → 多树均值 → tanh 映射到 [-1, 1]
```

**XGBoost 模型**（xgboost4j 集成）：
```
MLModelRegistry.getModel(symbol)
  └─ 查询 model_version 表，获取 active 状态的模型
       └─ hyperparametersJson 中的 modelType 分派
            ├─ "xgboost" → XGBoostModel.fromJson() 
            │    └─ Base64 解码 → XGBoost4j Booster
            │         └─ predict(features) → Booster 推理 → tanh 映射到 [-1, 1]
            └─ "random_forest" → RandomForestModel.fromJson()
```

XGBoost 模型的序列化格式为 `xgboost_base64:<base64-encoded-booster-bytes>`，
通过 `modelDataJson` 中的 `"xgboost_base64:"` 前缀与 RandomForest 的 JSON 格式自动区分。

**模型文件加载（生产环境）**：
```
SpreadCalculatorFactory.init()
  └─ config.alphaModelType == "xgboost"
       └─ XGBoostModel.loadModelFile(new File(alphaModelPath), name, featureCount)
            └─ alphaAggregator.registerMLModel(symbol, xgBoostModel)
```

`FeatureExtractor` 提取 10 维市场微观结构特征：

| 索引 | 特征 | 说明 | 公式 |
|------|------|------|------|
| 0 | log_return_1 | 1 周期对数收益率 | ln(P_t / P_{t-1}) |
| 1 | log_return_3 | 3 周期对数收益率 | ln(P_t / P_{t-3}) |
| 2 | log_return_5 | 5 周期对数收益率 | ln(P_t / P_{t-5}) |
| 3 | log_return_10 | 10 周期对数收益率 | ln(P_t / P_{t-10}) |
| 4 | volatility | 变异系数 | stddev(price) / mean(price) |
| 5 | spread_bps | 价差（基点） | (ask-bid)/mid × 10000 |
| 6 | imbalance | 订单簿不平衡度 | (bidVol-askVol)/(bidVol+askVol) |
| 7 | bid_volume | 买方深度（对数） | ln(ΣbidQty) |
| 8 | ask_volume | 卖方深度（对数） | ln(ΣaskQty) |
| 9 | price_level | 价格相对 MA(5) 偏离 | (price-MA5)/MA5 |

### 2.3 盈亏平衡价差钳制

引擎在每次计算挂单价后，自动应用盈亏平衡价差钳制，防止因手续费导致亏损：

```java
BigDecimal breakEven = 2 × takerFeeRate × midPrice;
BigDecimal minHalf = breakEven / 2;  // 最小单边偏移量

if (askOffset < minHalf) askOffset = minHalf;
if (bidOffset < minHalf) bidOffset = minHalf;
```

例如：takerFeeRate = 0.001, midPrice = 50000 → 单边最小偏移 50 个价格单位。这意味着挂单价不会过于接近中间价，确保每笔成交至少覆盖双边手续费。

---

## 三、回测引擎与撮合系统（核心）

### 3.1 主循环

`BacktestEngine.simulate()` 是整个回测的核心，按时间顺序逐快照执行：

```
for each snapshot:
  1. 记录中间价至 VolatilityTracker
  2. 更新近期价格序列（用于动量 Alpha）
  3. 若 alphaEnabled，计算 compositeAlpha
  4. 通过 SpreadCalculator 计算买卖偏移量
  5. 应用盈亏平衡价差钳制
  6. 计算 bidPrice = mid - bidOffset, askPrice = mid + askOffset
  7. 风险控制检查（熔断 / 最大回撤）
  8. 买方成交模拟（先 aggressive 再 passive）
  9. 卖方成交模拟（先 aggressive 再 passive）
 10. 记录熔断结果（success/failure）
 11. 计算盯市权益（mark-to-market）并加入 equityCurve
 12. 更新峰值和最大回撤
```

### 3.2 成交模拟

系统区分两种成交类型，分类使用不同的费率：

#### Aggressive Fill（吃单，Taker）

当挂单价格跨过价差时触发——买单 `bidPrice ≥ bestAsk`，卖单 `askPrice ≤ bestBid`。

```
takerFeeRate 应用于总成交额
使用 simulateFill() 跨多档位消耗流动性
```

`simulateFill()` 实现多档位流动性消耗：

```java
private FillResult simulateFill(List<PriceLevel> levels, SymbolBo symbolBo) {
    BigDecimal remaining = symbolBo.getMaxDelegateCount();
    BigDecimal totalFilled = 0, totalCost = 0;

    for (PriceLevel level : levels) {
        if (remaining <= 0) break;
        BigDecimal qty = min(level.quantity, remaining);
        totalFilled += qty;
        totalCost += qty × level.price;
        remaining -= qty;
    }

    return new FillResult(
        avgPrice = totalCost / totalFilled,
        filledQty = totalFilled,
        totalCost = totalCost
    );
}
```

#### Passive Fill（挂单，Maker）

当挂单价格落在价差内部时触发——买单 `bestBid < bidPrice < bestAsk`，卖单 `bestBid < askPrice < bestAsk`。

```
makerFeeRate 应用于总成交额
使用 estimateTopLevelQty() 取对手方 top 档位的挂单量
成交价为我们的挂单价（不是对手方价格）
```

`estimateTopLevelQty()`：
```java
BigDecimal qty = levels.get(0).getQuantity();          // 对手方 top 档位数量
if (qty > maxDelegateCount) qty = maxDelegateCount;    // 受最大委托量限制
return qty;
```

#### 成交判定条件总结

| 类型 | 买方条件 | 卖方条件 | 成交价 | 费率 |
|------|---------|---------|--------|------|
| Aggressive Buy | bidPrice ≥ bestAsk | — | bestAsk（多档均价） | takerFeeRate |
| Aggressive Sell | — | askPrice ≤ bestBid | bestBid（多档均价） | takerFeeRate |
| Passive Buy | bidPrice > bestBid | — | bidPrice | makerFeeRate |
| Passive Sell | — | askPrice < bestAsk | askPrice | makerFeeRate |

### 3.3 FIFO PnL 核算

采用先进先出（FIFO）原则匹配买卖订单，计算已实现盈亏：

```
买入队列 (buyQueue): LinkedList<FillRecord>
  每次买方成交 → 将 (fillPrice, fillQty) 入队尾

卖出时:
  computeFifoPnl(buyQueue, sellPrice, sellQty)
    for each buyFill in buyQueue (从头开始匹配):
      matchQty = min(remaining, buyFill.quantity)
      realizedPnl += matchQty × (sellPrice - buyFill.price)
      buyFill.quantity -= matchQty
      if buyFill.quantity == 0: 移出队列
```

示例：
```
时间 1: 买入 1 BTC @ 49999.9  → 队列 = [{price: 49999.9, qty: 1}]
时间 2: 买入 1 BTC @ 50004.9  → 队列 = [{49999.9, 1}, {50004.9, 1}]
时间 3: 卖出 1 BTC @ 50010.1  → 匹配第一笔: PnL = 1 × (50010.1 - 49999.9) = 10.2
                               队列 = [{50004.9, 1}]
```

### 3.4 资金不足时的部分成交

当账户余额不足以支付全部委托数量时，自动降为可负担的最大数量（类似 ai-hedge-fund 的 `max_quantity = int(cash / price)`）：

```java
if (balance < requiredCash) {
    BigDecimal maxAffordable = balance / (fillPrice + fee);  // RoundingMode.DOWN
    if (maxAffordable <= 0) continue;                        // 完全买不起，跳过
    fillQty = min(fillQty, maxAffordable);                   // 降量
    // 重新计算 cost 和 fee
}
```

此机制确保回测不会产生负余额，真实反映资金约束下的策略行为。

### 3.5 多快照数据流

```
快照 1: bestBid=49995, bestAsk=50005, mid=50000
        bidPrice=49999.9, askPrice=50000.1
        → passive buy   fill @ 49999.9 (qty=1)
        → passive sell  fill @ 50000.1 (qty=1)  PnL=0.2
        → equity = 100000.2

快照 2: bestBid=50000, bestAsk=50010, mid=50005
        bidPrice=50004.9, askPrice=50005.1
        → passive buy   fill @ 50004.9 (qty=1)
        → passive sell  fill @ 50005.1 (qty=1)  PnL=0.2
        → equity = 100000.4

快照 3: ...
```

---

## 四、绩效评估与分析（结果）

回测结果通过 `BacktestResult` 对象输出，包含 24 个字段，涵盖收益、风险、交易明细和增强指标四个维度。

### 4.1 核心收益指标

| 指标 | 计算方式 | 说明 |
|------|---------|------|
| 总收益率 | `(finalEquity - initialCapital) / initialCapital × 100%` | 整个回测期间的收益率 |
| 年化收益率 | `(1 + totalReturn)^(1/years) - 1` | 折算为年化 |
| 最终权益 | `balance + netPosition × lastMidPrice` | 盯市最终组合价值 |
| 总手续费 | `Σ(takerFeeRate × takerCost) + Σ(makerFeeRate × makerRevenue)` | 所有成交的手续费汇总 |

### 4.2 风险指标

| 指标 | 计算方式 | 说明 |
|------|---------|------|
| 最大回撤 | `max(peak - equity) / peak × 100%` | 权益从峰值到谷值的最大跌幅 |
| 夏普比率 | `mean(return) / std(return) × √(ticksPerYear)` | 风险调整后收益，>1 为可接受 |
| Calmar 比率 | `annualizedReturn / maxDrawdown` | 收益 vs 最大回撤，>1 为优秀 |

夏普比率计算细节：
```java
double[] returns = new double[equityCurve.size() - 1];
for (int i = 1; i < equityCurve.size(); i++) {
    returns[i-1] = (equityCurve[i] - equityCurve[i-1]) / equityCurve[i-1];
}
double mean = average(returns);
double stddev = sqrt(variance(returns));
double ticksPerYear = snapshots.size() / years;
sharpe = stddev > 0 ? mean / stddev * sqrt(ticksPerYear) : 0;
```

### 4.3 交易统计

| 指标 | 说明 |
|------|------|
| 总成交笔数 | 所有 buy + sell 交易 |
| 胜率 | `winningTrades / totalTrades × 100%` |
| 平均单笔 PnL | `Σ(realizedPnl) / totalTrades` |
| 盈亏比（利润因子） | `grossProfit / |grossLoss|`，若全部盈利则 9999 |

### 4.4 权益曲线

`equityCurve` 为 `List<BigDecimal>`，每个快照记录一个点：

```
equityCurve[i] = balance + netPosition × midPrice[i]
```

用于后续绘制净值曲线图、计算回撤曲线和滚动指标。

### 4.5 交易明细

每笔成交记录为 `BacktestTrade` 对象：

| 字段 | 类型 | 说明 |
|------|------|------|
| time | long | 成交时间戳 |
| side | String | "buy" 或 "sell" |
| price | BigDecimal | 成交均价 |
| quantity | BigDecimal | 成交数量 |
| fee | BigDecimal | 该笔手续费 |
| realizedPnl | BigDecimal | 已实现盈亏（仅 sell） |

### 4.6 报告生成

`BacktestReport` 提供文本格式的易读报告：

```java
// 生成摘要
String summary = BacktestReport.generateSummary(result);

// 生成详细报告（摘要 + 最近 50 笔交易）
String detail = BacktestReport.generateDetail(result);
```

输出示例：
```
┌──────────────────────────────────────────────────────────────┐
│                    Backtest Report                           │
├──────────────────────────────────────────────────────────────┤
│ Symbol:       BTCUSDT                                        │
│ Model:        constant                                       │
│ Period:       2023-11-15 00:00 ~ 2023-11-25 00:00            │
│ Initial Cap:  ¥100,000.00                                    │
│ Final Equity: ¥100,120.50         Return: +0.12%             │
│ Sharpe:       0.8542              Calmar: 0.0000              │
│ Max DD:       0.01%               Win Rate: 100.00%           │
│ Total Trades: 6                   Total Fees: ¥0.00           │
└──────────────────────────────────────────────────────────────┘
```

---

## 五、风险控制与验证（保障）

### 5.1 回测内风控

引擎内置两层风险检查，仅在 `riskEnabled = true` 时生效：

#### 熔断机制（CircuitBreakerRisk）

```
连续 N 次无成交 → 熔断器打开 → 跳过后续快照 → 冷却 M 毫秒后自动恢复
```

- `circuitBreakerThreshold`（默认 10）：连续失败次数阈值
- `circuitBreakerCooldownMs`（默认 60000）：冷却时间（毫秒）
- 每次成功成交重置失败计数

```java
if (circuitBreaker != null && !circuitBreaker.check(symbol, null, null, null)) {
    circuitBreaker.recordSuccess();  // 逐步恢复
    continue;  // 跳过本轮
}
```

#### 最大回撤控制

```java
BigDecimal currentEquity = balance + netPosition × midPrice;
BigDecimal ddPct = (peak - currentEquity) / peak × 100;
if (ddPct >= maxDrawdownLimit) continue;  // 回撤超限，跳过
```

`maxDrawdownPercent` 默认 10.0，代表权益从峰值下跌 10% 后停止交易。

### 5.2 风控与生产环境的对应

| 风控类型 | 回测实现 | 生产环境实现 |
|---------|---------|-------------|
| 熔断 | `CircuitBreakerRisk`（回测内直接创建） | `CircuitBreakerRisk`（Spring Bean） |
| 最大回撤 | 简单百分比检查（`simulate` 内） | `MaxDrawdownRisk` + `PortfolioRiskManager` |
| 价格偏离 | 无（回测使用历史数据） | `PriceDeviationRisk` |
| 集中度 | 无（回测仅单币种） | `ConcentrationRisk` + `PortfolioRiskManager` |
| 订单数限制 | 无（回测无实际订单） | `MaxOrderCountRisk` |

### 5.3 VolatilityTracker 状态隔离

每次 `simulate()` 开始时调用 `volatilityTracker.clear(symbol)` 清除历史价格数据，防止前一次回测的状态泄漏到下一次：

```java
volatilityTracker.clear(config.getSymbol());
```

### 5.4 风控测试

`RiskControlChaosTest` 包含 32 个混沌工程测试，覆盖熔断、价格偏离、最大回撤、集中度、订单数限制等全部风控组件。详见第六节。

---

## 六、数据采集

### 6.1 订单簿快照采集

实盘运行时，系统通过交易所 WebSocket 行情流持续接收订单簿增量更新。订单簿快照的持久化由以下路径完成：

```
Exchange WebSocket (Bybit / Binance / Bitget)
  → LMAX Disruptor RingBuffer
    → OrderBookStore (内存)
      → OrderBookSnapshotMapper.insert()
        → TiDB (order_book_snapshot 表)
```

### 6.2 用于回测的 CSV 数据采集

回测支持从 CSV 文件加载历史数据。CSV 数据可以通过以下方式准备：

1. **从数据库导出**：直接导出 `order_book_snapshot` 表到 CSV
2. **从交易所历史数据 API 获取**：部分交易所提供历史订单簿快照下载
3. **手动构造**：用于单元测试的小样本数据

### 6.3 ML 训练数据采集

回测引擎的 ML 功能依赖预先训练的模型，而训练数据由实盘运行时自动采集：

```
StrategyExecutor.execute() tick 路径:
  每 N 个 tick → TrainingDataCollector.captureFeatures()
    → FeatureExtractor.extract() → 10 维特征向量
    → 暂存为待标记样本

  后续 tick → 价格更新 → 生成 label（收益率）
    → 持久化到 train_dataset 表

  ModelTrainerService.train() → 加载训练数据
    → CART 回归树训练 → 随机森林集成
    → 保存到 model_version 表
```

---

## 七、测试覆盖

### 7.1 测试文件与数量

| 测试文件 | 测试数 | 类型 | 说明 |
|---------|--------|------|------|
| `BacktestEngineTest.java` | 13 | 单元测试 | 回测引擎核心逻辑 |
| `CsvSnapshotLoaderTest.java` | 5 | 单元测试 | CSV 数据加载 |
| `RiskControlChaosTest.java` | 32 | 混沌工程 | 风控组件全链路 |
| `MLInferenceBenchmark.java` | — | 性能基准 | ML 推理延迟测试 |

### 7.2 BacktestEngineTest 测试案例详解

| 编号 | 测试方法 | 场景 | 验证点 | 边界条件 |
|------|---------|------|--------|---------|
| 1 | `passiveBuyFillsWhenBidInsideSpread` | 正常挂单买入 | buy trade 存在，side=buy | offset=1 < halfSpread=5 |
| 2 | `passiveSellFillsWhenAskInsideSpread` | 正常挂单卖出 | buy + sell 两笔成交，sell PnL 为正 | offset=1 < halfSpread=5 |
| 3 | `noFillWhenOffsetExceedsHalfSpread` | 价差过大无成交 | totalTrades=0, trades.size=0 | offset=60 > halfSpread=5 |
| 4 | `passiveFillUsesMakerFee` | 挂单使用 maker 费率 | totalFees > 0 | maker=0.1%, taker=0.0001% |
| 5 | `multiLevelOrderBookProcessesSuccessfully` | 多档位盘口处理 | 成交 >= 2 笔 | 3 档买卖盘口深度 |
| 6 | `fifoPnlIsCorrect` | 多笔买卖 FIFO 配对 | 累计 realizedPnl > 0 | 3 个快照，价格递增 |
| 7 | `circuitBreakerBlocksAfterThresholdFailures` | 熔断机制 | 0 成交，余额不变 | threshold=2, 3 个快照 |
| 8 | `maxDrawdownDoesNotCrashEngine` | 最大回撤不崩溃 | result 非空，ticks > 0 | 价格下跌 100 bps |
| 9 | `partialFillWhenCashInsufficient` | 资金不足部分成交 | trades > 0, 余额 >= 0 | 3 倍盘口量 vs 本金 |
| 10 | `equityCurveTracksPortfolioValue` | 权益曲线追踪 | equityCurve size = 快照数 | 2 个快照 |
| 11 | `emptySnapshotsReturnsEmptyResult` | 空快照列表 | 0 ticks, 0 trades | List.of() |
| 12 | `nullMidPriceSkipsSnapshot` | 无效中间价跳过 | trades > 0（跳过第 1 个） | midPrice=0 |
| 13 | `resultMetricsArePopulated` | 结果字段完整 | id/symbol/model/ticks/equityCurve | 3 个快照 |

### 7.3 CsvSnapshotLoaderTest 测试案例

| 编号 | 测试方法 | 场景 | 验证点 |
|------|---------|------|--------|
| 1 | `loadFullBookCsv` | 完整订单簿 CSV 解析 | 3 个快照，bestBid/bestAsk/多档位正确 |
| 2 | `loadPriceOnlyCsv` | 纯价格 CSV 解析 | 3 个快照，bestBid=bestAsk=midPrice |
| 3 | `loadCsvFromInputStream` | 从 InputStream 加载 | 与文件加载结果一致 |
| 4 | `loadCsvWithNonexistentFile` | 文件不存在 | 抛出 IOException |
| 5 | fixture 文件存在验证 | 测试资源完整性 | CSV 文件可读 |

### 7.4 测试案例覆盖矩阵

```
功能模块              BacktestEngineTest  CsvSnapshotLoaderTest  RiskControlChaosTest
─────────────────────────────────────────────────────────────────────────────────────
被动买入成交                  ✓
被动卖出成交                  ✓
价差过大无成交                ✓
Maker 费率                    ✓
多档位盘口                    ✓
FIFO PnL                      ✓
熔断风控                      ✓                                  ✓
最大回撤                      ✓                                  ✓
部分成交                      ✓
权益曲线                      ✓
空快照                        ✓
无效数据跳过                  ✓
结果字段                      ✓
CSV 完整格式解析                                  ✓
CSV 纯价格解析                                   ✓
CSV 流式加载                                     ✓
CSV 文件不存在                                   ✓
熔断详细行为                                                        ✓
价格偏离                                                           ✓
集中度                                                             ✓
订单数限制                                                         ✓
风控链全链路                                                       ✓
速率限制                                                           ✓
混沌组合场景                                                       ✓
```

### 7.5 测试设计原则

1. **零 Mock 策略层**：使用真实的 `VolatilityTracker`，Mock 只用于无法实例化的依赖（Mapper、Store、MLModelRegistry）
2. **零费率基础配置**：基础配置使用零费率，避免盈亏平衡价差钳制干扰成交逻辑；需要测试费率的场景单独配置
3. **确定性成交条件**：使用 `ConstantSpreadCalculator` + 精确计算的偏移量，确保成交/不成交的边界确定
4. **完整 Input/Output 验证**：既验证结果字段完整性，也验证内部状态（如 FIFO 队列的 realizedPnl）
5. **异常路径覆盖**：空快照列表、无效中间价、文件不存在、解析失败等异常路径均有测试

---

## 八、REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/backtest/run` | 运行回测（从数据库加载数据） |
| POST | `/api/backtest/run-csv` | 从上传的 CSV 运行回测 |
| GET | `/api/backtest/results` | 列出所有缓存结果 |
| GET | `/api/backtest/results/{id}` | 获取单个结果 |
| GET | `/api/backtest/results/{id}/report` | 获取文本格式报告 |
| DELETE | `/api/backtest/results` | 清空缓存结果 |

---

## 九、回测配置参考

```json
{
  "symbol": "BTCUSDT",
  "startTime": 1700000000000,
  "endTime": 1700100000000,
  "model": "constant",
  "initialCapital": 100000,
  "makerFeeRate": 0.001,
  "takerFeeRate": 0.001,
  "riskEnabled": true,
  "alphaEnabled": false,
  "alphaModelName": null,
  "exchange": "BYBIT",
  "maxDrawdownPercent": 10.0,
  "circuitBreakerThreshold": 10,
  "circuitBreakerCooldownMs": 60000,
  "modelParams": {
    "tickSize": 0.1,
    "stepSize": 0.001,
    "minSize": 0.001,
    "maxDelegateCount": 10,
    "baseOffsetTicks": 1,
    "targetPosition": 0,
    "maxPosition": 1,
    "skewFactor": 0.5,
    "volCoeff": 2.0
  }
}
```

**关键参数说明：**

| 参数 | 说明 | 对回测结果的影响 |
|------|------|-----------------|
| `model` | 价差模型（constant/inventory/risk/hybrid） | 决定挂单价格的偏移计算方式 |
| `baseOffsetTicks` | 基础偏移量（tick 数） | 乘以 tickSize 得到价格偏移，直接影响成交概率 |
| `makerFeeRate` | 挂单费率 | 影响被动成交的净收益 |
| `takerFeeRate` | 吃单费率 | 影响主动成交的净收益，同时影响盈亏平衡价差钳制 |
| `maxDelegateCount` | 最大委托数量 | 限制单次成交的最大数量 |
| `riskEnabled` | 是否启用风控 | 熔断和最大回撤检查，建议始终启用 |
| `alphaEnabled` | 是否启用 Alpha | 启用后 AutoG 根据市场信号动态调整库存目标 |
