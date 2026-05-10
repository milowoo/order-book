# OrderBook Market Maker — 高级加密货币高频做市系统

基于 Java + Spring Boot 的加密货币高频做市系统。聚合多交易所行情，以 Bybit 订单簿作为参考定价，在目标交易所执行铺单策略。覆盖策略建模、订单簿分析、Alpha 信号开发、机器学习预测与训练、组合风控、智能路由和 Prometheus 监控等完整技术栈。

---

## 整体架构

系统按模块化分层设计，每层职责清晰：

```
交易所行情流 (Bybit / Binance / Bitget)
       │
       ▼
  OrderBookStore      ← 统一订单簿聚合
       │
       ▼
  SpreadCalculator    ← 价差计算（4 种模型）
  AlphaAggregator     ← Alpha 信号（订单流 / 动量 / ML）
  RiskControl         ← 风控检查（熔断 / 回撤 / 订单数 / 偏离 / 集中度）
  PortfolioRiskManager← 组合风控（VaR / Sharpe / 相关性 / 集中度）
       │
       ▼
  SOREngine           ← 智能路由（最优交易所选择）
       │
       ▼
  OrdersMakerImpl     ← 同步订单簿 + 铺单执行
```

## 项目模块

| 模块 | 职责 |
|------|------|
| `orderbook-core` | 核心：交易所连接、订单簿管理、策略执行、Alpha 信号、ML 推理与训练、组合风控、SOR、Prometheus 监控 |
| `orderbook-command` | 命令接口层：StrategyCmd、ExchangeFunc 等 |
| `orderbook-connector` | 交易所连接器封装 (xchange 集成) |

---

## 做市价差模型 (Spread Models)

系统支持 4 种可配置的价差模型，控制挂单价格相对于参考订单簿 (Bybit) 的偏移量。价差计算是高频做市策略的核心环节，直接影响做市盈亏和库存风险。

```
OrdersMakerImpl.adjustOrderBook()
  └─ SpreadCalculatorFactory.getCalculator(symbol)
       ├── ConstantSpreadCalculator         固定价差
       ├── InventoryBasedSpreadCalculator   库存依赖 → AccountStore
       ├── RiskAdjustedSpreadCalculator     风险补偿 → VolatilityTracker
       └── HybridSpreadCalculator           混合模型 → 加权合并
```

### 1. 固定价差模型 (Constant Spread)

固定 `baseOffsetTicks * tickSize` 的偏移量，买卖对称。适用于币种选择初期的简单做市策略。

### 2. 库存依赖模型 (Inventory-Based Spread)

根据 `AccountStore` 中的 Base Token 净持仓动态调整价差，是不对称价差策略的核心实现：

```
positionRatio = clamp((balance - target) / maxPos, -1, 1)

做多时 → 买单价差扩大（抑制买入）+ 卖单价差缩小（鼓励卖出）→ 降低库存
做空时 → 买单价差缩小（鼓励买入）+ 卖单价差扩大（抑制卖出）→ 积累库存
```

当股价向不利方向运动时，主动调整买卖报价以加速库存回归目标水平，是高频做市策略的核心风控手段。

### 3. 风险补偿模型 (Risk-Adjusted Spread)

高波动时自动扩大价差，降低风险敞口：

- 基于 `VolatilityTracker` 的滚动变异系数（CV = stddev / mean）
- 公式：`offset = base * (1 + volCoeff * volatility)`，最低 1 倍
- 波动率窗口可配置（默认 20 tick）

### 4. 混合模型 (Hybrid Spread)

加权组合 Constant + Inventory + Risk 三种模型，默认权重 0.3 / 0.4 / 0.3：

- 低波动时以库存管理为主
- 高波动时自动切换为风险规避
- 参数化配置，可按 symbol 独立调参

---

## 市场微观结构分析

系统对订单簿进行多维度实时分析，为做市策略和 Alpha 信号提供数据基础：

- **订单簿深度扫描**：逐档扫描 bid/ask 盘口，计算各档位量价分布
- **订单流不平衡**：实时买卖压力指标 `(bidVol - askVol) / (bidVol + askVol)`
- **价差分析**：spread_bps = 买卖价差以基点计量
- **价格发现**：价格相对 MA(5) 偏离度，用于均值回归信号

---

## Alpha 模型

Alpha 模型通过分析市场微观结构生成短期方向性信号，动态调整库存目标，使做市策略在趋势行情中顺势积累库存、在逆势中降低风险敞口：

```
AlphaAggregator.getAlpha(symbol)
  ├── OrderFlowImbalanceAlpha   订单流不平衡 → OrderBookStore
  └── MomentumAlpha             价格动量     → VolatilityTracker
       │
       └── CompositeAlpha       加权合成 → [-1, 1]
              │
              ▼
InventoryBasedSpreadCalculator.calculateOffset()
  └─ targetPosition = baseTarget + alpha * maxAdjustment
```

### 信号说明

**订单流不平衡**：基于订单簿买卖盘口深度不平衡计算实时买卖压力。深度档位可配置（默认前 10 档）。

**价格动量**：基于 VolatilityTracker 记录的历史中间价计算 ROC，通过 tanh 映射到 [-1, 1] 避免极端值，回溯周期可配置（默认 5 tick）。

**复合 Alpha**：加权平均多个信号形成最终方向性判断。

### Alpha 与库存模型的联动

```
adjustedTarget = baseTarget + alpha × maxAlphaPositionAdjustment

alpha > 0（看涨）→ target 提高 → 倾向于买入 → 积累库存
alpha < 0（看跌）→ target 降低 → 倾向于卖出 → 降低库存
```

### Alpha 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `alphaEnabled` | false | 是否启用 Alpha 信号 |
| `alphaOrderFlowDepth` | 10 | 订单流计算深度 |
| `alphaOrderFlowWeight` | 0.5 | 订单流信号权重 |
| `alphaMomentumLookback` | 5 | 动量回溯 tick 数 |
| `alphaMomentumWeight` | 0.5 | 动量信号权重 |
| `alphaMaxPositionAdjustment` | 0.5 | 目标持仓最大偏移量 |
| `alphaModelType` | — | ML 模型类型 (`random_forest`) |
| `alphaModelPath` | — | 序列化模型文件路径 |
| `alphaModelName` | default | 模型名称 |
| `alphaMlWeight` | 0.3 | ML 信号权重 |

---

## 机器学习在交易中的应用

系统内置纯 Java 实现的随机森林 (Random Forest) 推理与训练管线，将 ML 模型的预测作为 Alpha 信号参与价差计算。

### 推理架构

```
MLModel (接口)
 └── RandomForestModel    决策树集成 → 均值输出
      └── DecisionTree    二叉树 → JSON 序列化

MLAlphaSignal (AlphaSignal 实现)
 ├── FeatureExtractor   → 提取 10 维市场微观结构特征
 └── MLModel.predict()  → 预测值 → tanh 映射到 [-1, 1]
      │
      ▼
AlphaAggregator.registerMLModel() → CompositeAlpha 加权合成
```

### 训练管线

系统支持在运行时收集训练数据、训练模型、评估并激活新模型：

```
TrainingDataCollector        ← 每 N 个 tick 从策略执行中捕获特征
       │
       ▼
  train_dataset 表 (MySQL)   ← 存储 (features_json, label)
       │
       ▼
  ModelTrainerService.train(symbol, config)
       │
       ├── CartTrainer              CART 回归树训练（MSE 分裂）
       ├── RandomForestTrainer      Bagging + 特征子采样
       └── ModelEvaluator           R² / MAE / RMSE
              │
              ▼
  model_version 表 (MySQL)    ← 存储模型 + 超参数 + 评估指标
       │
       ▼
  MLModelRegistry.activateModel()  ← 运行时热切换推理模型
```

#### CART 回归树

`CartTrainer` 实现 CART (Classification And Regression Tree) 训练算法：
- 递归二分分裂，最小化加权 MSE
- 支持 `maxDepth`、`minSamplesLeaf` 停止条件
- 特征子采样接口供 RandomForest 使用

#### 随机森林训练

`RandomForestTrainer` 使用 Bagging + 特征子采样训练多棵 CART 树：
- 每棵树在 bootstrap 样本上训练，随机选取 `featureRatio * totalFeatures` 个特征
- 预测为所有树输出均值

#### 训练数据收集

`TrainingDataCollector` 在策略 tick 路径中自动采集训练数据：
- 每 N 个 tick 提取一次特征（通过 `FeatureExtractor`）
- 延迟标记：当前价格相对采集时价格的收益率作为 label
- 数据持久化到 `train_dataset` 表

#### 模型版本管理

`ModelVersionService` 管理已训练模型的完整生命周期：
- 每次训练生成一条 `model_version` 记录（含模型数据、超参数、指标）
- 支持激活/切换：`activateModel(id)` → 自动去活旧模型
- `MLModelRegistry` 缓存活跃模型，支持运行时热切换

#### 自动训练

`@Scheduled autoTrain()` 每小时检查一次，当新数据量超过阈值时自动触发重训练。

### 特征工程

`FeatureExtractor` 从市场数据中提取 10 维特征向量，覆盖收益率、波动率、价差、不平衡度、深度分布和价格水平：

| 索引 | 特征 | 说明 | 数据来源 |
|------|------|------|---------|
| 0 | log_return_1 | 1 周期对数收益率 | VolatilityTracker |
| 1 | log_return_3 | 3 周期对数收益率 | VolatilityTracker |
| 2 | log_return_5 | 5 周期对数收益率 | VolatilityTracker |
| 3 | log_return_10 | 10 周期对数收益率 | VolatilityTracker |
| 4 | volatility | 变异系数 | VolatilityTracker |
| 5 | spread_bps | 买卖价差 (基点) | OrderBookStore |
| 6 | imbalance | 订单簿不平衡度 [-1, 1] | OrderBookStore |
| 7 | bid_volume | 买单总深度 (对数) | OrderBookStore |
| 8 | ask_volume | 卖单总深度 (对数) | OrderBookStore |
| 9 | price_level | 价格相对 MA(5) 偏离度 | VolatilityTracker |

### 决策树 (DecisionTree)

二叉树结构，内部节点按 `featureIndex <= threshold` 分裂，叶子节点存储预测值。JSON 序列化使用手写递归下降解析器，不依赖第三方库。

### 随机森林 (RandomForestModel)

集成 N 棵决策树，输出为所有树预测均值。Jackson JSON 序列化，支持 `save(File)` / `load(File)` / `fromJson(String)` / DB 持久化。

### 集成路径

ML 模型在 `SpreadCalculatorFactory` 中注册到 `AlphaAggregator`：

```
SpreadCalculatorFactory.buildCalculator()
  ├── 读取 alphaModelType / alphaModelPath
  ├── RandomForestModel.load(file) → MLModel
  └── alphaAggregator.registerMLModel(symbol, model)
```

训练后的模型通过 `MLModelRegistry.getModel(symbol)` 获取，支持 DB 或文件加载。

### 扩展性

系统通过 `MLModel` 接口支持扩展其他模型。引入 xgboost4j 或 lightgbmlib 依赖后，实现 `MLModel` 接口即可接入 XGBoost / LightGBM。

### ML 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `ml.auto.train.interval.hours` | 1 | 自动训练间隔 (小时) |
| `ml.training.data.max.samples` | 10000 | 每 symbol 最大训练样本数 |
| `ml.label.lookahead.periods` | 5 | 标记生成的前瞻周期 |
| `ml.feature.capture.interval.ticks` | 5 | 特征捕获间隔 (tick) |

---

## 多层次风险控制

### 单 tick 风控链

策略执行时按顺序通过以下风控检查，任一未通过则跳过本轮交易：

```
StrategyExecutor.execute()
  ├── CircuitBreakerRisk      熔断：连续失败 N 次后冷却 M ms
  ├── MaxDrawdownRisk         最大回撤：组合价值回撤超阈值停止
  ├── MaxOrderCountRisk       订单数量：活跃订单数超限停止
  ├── PriceDeviationRisk      价格偏离：挂单价格偏离市场价过远
  ├── ConcentrationRisk       集中度：单币持仓超组合比例阈值
  └── OrdersMakerImpl         铺单执行（使用 SpreadCalculator 计算价差）
```

### 风控参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `risk.circuit.breaker.threshold` | 10 | 触发熔断的连续失败次数 |
| `risk.circuit.breaker.cooldown.ms` | 60000 | 熔断冷却时间 (ms) |
| `risk.max.drawdown.percent` | 10.0 | 最大回撤百分比 |
| `risk.price.deviation.percent` | 5.0 | 最大价格偏离百分比 |
| `active.orders.number.limit` | 1500 | 最大活跃订单数 |
| `risk.portfolio.concentration.limit` | 0.30 | 单币集中度上限 (30%) |

同时，`RiskAdjustedSpreadCalculator` 在波动率层面提供自适应风控：高波动时自动扩大价差降低风险敞口。

### 组合风控 (Portfolio Risk)

`PortfolioRiskManager` 每 5 秒刷新组合级风险指标：

```
PortfolioRiskManager.refresh()
  ├── totalPortfolioValue     = Σ(netPosition × midPrice) + freeUSDT
  ├── drawdownPct             = (peak - current) / peak
  ├── portfolioVaR95          = 历史模拟法 5% 分位
  ├── portfolioSharpeRatio    = 年化夏普比率 (×√252)
  ├── correlationMatrix       = 各币种 Pearson 相关系数
  └── symbolConcentration     = 各币种占比百分比
```

这些指标通过 Prometheus 暴露：
- `orderbook_portfolio_value` — 组合总净值
- `orderbook_portfolio_drawdown_pct` — 组合回撤比例
- `orderbook_portfolio_var_95` — 95% VaR
- `orderbook_portfolio_sharpe_ratio` — 年化夏普比
- `orderbook_portfolio_concentration{symbol="..."}` — 单币集中度

### 组合风控参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `risk.portfolio.concentration.limit` | 0.30 | 单币集中度上限 |
| `risk.portfolio.var.periods` | 20 | VaR 历史窗口期数 |
| `risk.portfolio.refresh.interval.ms` | 5000 | 刷新间隔 (ms) |

---

## 延迟优化 (Latency Optimization)

系统在关键路径上实施了多项延迟优化：

### 本地订单缓存

移除 1 秒 tick 中的 REST 调用：

```
旧路径：每 tick → OpenOrdersStore.getRemoteOpenOrders() → REST API (同步)
新路径：每 tick → OpenOrdersStore.getCachedOpenOrders() → 本地内存 (纳秒级)
```

- 私有 WebSocket 订单更新事件实时维护缓存
- `reloadFromRemote()` 保留为兜底重同步（每 12 分钟执行一次）

### 令牌桶信号量

```java
// 旧实现：自旋等待
while (bucket <= 0) { Thread.sleep(10); }

// 新实现：Semaphore + ScheduledExecutorService 定时补充
semaphore.acquire(permits);  // 阻塞而非自旋
```

### LMAX Disruptor 环形缓冲区

环缓冲区大小从 1024 增至 8192，减少高频行情下的争用。

### 热路径内存分配优化

- `AbstractSymbolOrderBooks.get()`：`stream().map().collect(toList())` → 手动 `for` 循环 + 预分配 `ArrayList`
- `OrdersMakerImpl.*`：流操作替换为手动循环

---

## Smart Order Routing (SOR)

智能路由引擎，根据实时交易所数据选择最优场所执行订单：

```
OrdersMakerImpl.call()
  └── resolveExchange(symbol)  →  是否启用 SOR？
       ├── 否 → 使用默认交易所 (OSL_GLOBAL)
       └── 是 → SOREngine.selectExchange()
                  └── rankExchanges(symbol)
                       ├── LOWEST_FEE    → 最低费率
                       ├── FASTEST       → 最低延迟
                       ├── BEST_LIQUIDITY→ 最大流动性
                       └── WEIGHTED      → 综合评分
```

### 交易所统计

`RoutingTable` 每 10 秒刷新各交易所实时数据：

| 维度 | 数据来源 | 说明 |
|------|---------|------|
| takerFeeRate | FeeService | 吃单费率 |
| makerFeeRate | FeeService | 挂单费率 |
| avgLatencyMicros | 运行时测量 (EMA) | 历史延迟指数移动平均 |
| healthy | exchange handler | 连接健康状态 |

### 加权评分

WEIGHTED 策略使用综合评分：
```
score = 0.4 × feeScore + 0.3 × latScore + 0.3 × liqScore
```

- feeScore = 1 / (1 + takerFeeRate × 1000)
- latScore = 1 / (1 + avgLatencyMicros / 1000)
- liqScore = fillProbabilityProxy

### REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/sor/stats` | GET | 所有交易所实时统计 |
| `/api/sor/rank/{symbol}` | GET | 按策略排序的交易所列表 |
| `/api/sor/config` | GET | 当前 SOR 配置 |

### SOR 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `sor.enabled` | false | SOR 总开关 |
| `sor.strategy` | lowest_fee | 路由策略 |
| `sor.primary.exchange` | OSL_GLOBAL | 默认交易所 |
| `sor.fallback.exchanges` | — | 备用交易所列表 |

---

## 监控与指标 (Prometheus)

系统通过 Micrometer + Prometheus 暴露完整监控指标：

| 指标 | 类型 | 说明 |
|------|------|------|
| `orderbook_pnl_realized` | Gauge | 已实现盈亏总额 |
| `orderbook_pnl_unrealized` | Gauge | 未实现盈亏总额 |
| `orderbook_position_net` | Gauge | 净持仓汇总 |
| `orderbook_connection_healthy` | Gauge | 交易所连接状态 (tag: exchange) |
| `orderbook_strategy_latency` | Timer | 策略执行延迟 (P50/P95/P99) |
| `orderbook_arbitrage_opportunities_total` | Gauge | 套利机会总数 |
| `orderbook_portfolio_value` | Gauge | 组合总净值 |
| `orderbook_portfolio_drawdown_pct` | Gauge | 组合回撤比例 |
| `orderbook_portfolio_var_95` | Gauge | VaR(95) |
| `orderbook_portfolio_sharpe_ratio` | Gauge | 年化夏普比 |
| `orderbook_portfolio_concentration` | Gauge | 单币集中度 (tag: symbol) |
| `orderbook_sor_routing_total` | Gauge | SOR 路由决策计数 |

---

## 策略执行流程

每个交易对 (symbol) 有独立定时任务线程，按 `updateIntervalMs` 间隔执行完整 tick 循环：

```
1. 记录中间价 (VolatilityTracker 更新)
2. 风控检查：熔断 / 回撤 / 订单数 / 价格偏离 / 集中度
3. CancelOrderBookOutOrder  — 撤销订单簿范围外的挂单
4. UpdateOrderCount         — 更新活跃订单计数
5. MaxDrawdownRisk.update   — 更新组合价值
6. OrdersMakerImpl          — 同步订单簿并铺单
   ├── resolveExchange()     — SOR 选择目标交易所（可选）
   ├── adjustOrderBook()     — SpreadCalculator 计算价格偏移
   ├── processOnlyInOSL()    — 撤除本地已有、bybit 已无的订单
   ├── processOnlyInBybit()  — 补充 bybit 有、本地没有的订单
   ├── processOrders()       — 调整数量偏离容忍区间的订单
   ├── batchCancelOrder()    — 批量撤单
   └── batchPlaceOrder()     — 批量下单
7. PortfolioRiskManager      — 每 5 秒刷新组合风控指标
8. MetricsService.refreshAll() — 更新 Prometheus 指标
```

---

## 配置示例

交易所配置：

```yaml
connect:
  exchange:
    BYBIT:
      stream_public_use: true     # 接收参考行情
      stream_private_use: false
    OSL_GLOBAL:
      stream_public_use: true     # 目标交易所
      stream_private_use: true    # 铺单执行 + 账户流
```

价差与 Alpha 配置：

```yaml
strategy:
  spread:
    symbols:
      BTCUSDT:
        model: hybrid
        baseOffsetTicks: 1.0
        targetPosition: 0.0
        maxPosition: 1.0
        skewFactor: 0.5
        volCoeff: 2.0
        volatilityWindowSize: 20
        constantWeight: 0.3
        inventoryWeight: 0.4
        riskWeight: 0.3
        alphaEnabled: true
        alphaOrderFlowDepth: 10
        alphaOrderFlowWeight: 0.5
        alphaMomentumLookback: 5
        alphaMomentumWeight: 0.5
        alphaMaxPositionAdjustment: 0.5
        alphaModelType: random_forest
        alphaModelPath: models/btc_rf_model.json
        alphaModelName: btc_rf_v1
        alphaMlWeight: 0.3
      ETHUSDT:
        model: constant
        baseOffsetTicks: 1.0
```

---

## 本地开发

```bash
# 编译
mvn compile -pl orderbook-core -am

# 启动 (local profile)
java -Dspring.profiles.active=local \
     -jar orderbook-core/target/orderbook-core.jar
```

注意事项：
- 本地环境不连接 Apollo，配置在 `application-local.yml`
- Bybit 行情流默认开启，可接收参考订单簿数据
- 策略自动启动开关在 `StrategyBootstrap.isAutoStartup()`，当前为 `false`
