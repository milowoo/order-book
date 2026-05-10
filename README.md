# OrderBook Market Maker

基于 Java + Spring Boot 的加密货币做市商系统。聚合多交易所行情，使用 Bybit 订单簿作为参考，在指定交易所执行铺单策略。

## 项目模块

| 模块 | 职责 |
|------|------|
| `orderbook-core` | 核心：交易所连接、订单簿管理、策略执行、风控 |
| `orderbook-command` | 命令接口层：定义 StrategyCmd、ExchangeFunc 等 |
| `orderbook-connector` | 交易所连接器封装 (xchange 集成) |
| `orderbook-strategy` | 策略接口 + 风控框架 (无业务依赖) |

## 价差模型

系统支持 4 种可配置的价差计算模型，用于控制挂单价格相对于参考订单簿 (Bybit) 的偏移量。

### 架构

```
OrdersMakerImpl.adjustOrderBook()
  └─ SpreadCalculatorFactory.getCalculator(symbol)
       ├── ConstantSpreadCalculator         固定价差
       ├── InventoryBasedSpreadCalculator   库存依赖 → AccountStore
       ├── RiskAdjustedSpreadCalculator     风险补偿 → VolatilityTracker
       └── HybridSpreadCalculator           混合模型 → 加权合并以上
```

### 模型说明

#### 1. 固定价差模型 (Constant Spread)
- 返回固定 `baseOffsetTicks * tickSize` 的偏移量，买卖对称
- 默认值：1 tick
- 适用场景：简单稳定的做市策略

#### 2. 库存依赖模型 (Inventory-Based Spread)
- 根据 `AccountStore` 中的 Base Token 净持仓动态调整价差
- 做多时：买单价差扩大（抑制买入），卖单价差缩小（鼓励卖出）
- 做空时：相反
- 公式：`positionRatio = clamp((balance - target) / maxPos, -1, 1)`
  - bidOffset = base \* (1 + skew \* positionRatio)
  - askOffset = base \* (1 - skew \* positionRatio)

#### 3. 风险补偿模型 (Risk-Adjusted Spread)
- 高波动时扩大价差，降低风险敞口
- 基于 VolatilityTracker 的滚动变异系数 (stddev/mean)
- 公式：`offset = base * (1 + volCoeff * volatility)`，最低 1 倍

#### 4. 混合模型 (Hybrid Spread)
- 加权组合 Constant + Inventory + Risk 三种模型
- 默认权重：Constant 0.3 / Inventory 0.4 / Risk 0.3

### 配置示例

```yaml
strategy:
  spread:
    symbols:
      BTCUSDT:
        model: hybrid                    # constant/inventory/risk/hybrid
        baseOffsetTicks: 1.0
        targetPosition: 0.0              # 目标持仓
        maxPosition: 1.0                 # 持仓归一化因子
        skewFactor: 0.5                  # 库存偏移强度
        volCoeff: 2.0                    # 波动率系数
        volatilityWindowSize: 20         # 波动率滚动窗口
        constantWeight: 0.3
        inventoryWeight: 0.4
        riskWeight: 0.3
        alphaEnabled: true               # 启用 Alpha 信号
        alphaOrderFlowDepth: 10          # 订单流深度档位
        alphaOrderFlowWeight: 0.5        # 订单流信号权重
        alphaMomentumLookback: 5         # 动量回溯周期数
        alphaMomentumWeight: 0.5         # 动量信号权重
        alphaMaxPositionAdjustment: 0.5  # 目标持仓最大偏移量
      ETHUSDT:
        model: constant
        baseOffsetTicks: 1.0
```

## Alpha 模型

Alpha 模型通过分析市场微观结构生成短期方向性信号，动态调整库存目标，使做市策略在趋势行情中顺势积累库存、在逆势中降低风险敞口。

### 架构

```
AlphaAggregator.getAlpha(symbol)
  ├── OrderFlowImbalanceAlpha   订单流不平衡 → OrderBookStore (Bybit)
  └── MomentumAlpha             价格动量     → VolatilityTracker
       │
       └── CompositeAlpha       加权合成 → [-1, 1]
              │
              ▼
InventoryBasedSpreadCalculator.calculateOffset()
  └─ targetPosition = baseTarget + alpha * maxAdjustment
```

### 信号说明

#### 1. 订单流不平衡 (Order Flow Imbalance)
- 基于 Bybit 订单簿的买卖盘口深度不平衡
- 公式：`imbalance = (bidVolume - askVolume) / (bidVolume + askVolume)`
- 正值表示买单压力大（看涨），负值表示卖单压力大（看跌）
- 深度档位可配置（默认取前 10 档）

#### 2. 价格动量 (Momentum)
- 基于 VolatilityTracker 记录的历史中间价计算收益率
- 公式：`momentum = (currentPrice - priceNPeroidsAgo) / priceNPeroidsAgo`
- 通过 tanh 函数映射到 [-1, 1]，避免极端值
- 回溯周期可配置（默认 5 tick）

#### 3. 复合 Alpha (Composite)
- 加权平均多个信号：`复合Alpha = Σ(signal_i * weight_i) / Σ(weight_i)`
- 默认权重：订单流 0.5，动量 0.5

### 与库存模型的联动

Alpha 信号通过调整 `InventoryBasedSpreadCalculator` 的目标持仓来影响实际挂单价差：

```
adjustedTarget = baseTarget + alpha * maxAlphaPositionAdjustment

做多信号 (alpha > 0):
  → target 提高 → 相对当前持仓偏"空"
  → 买单 spread 收窄（鼓励买入）/ 卖单 spread 扩大（鼓励卖出）
  → 结果：积累库存

做空信号 (alpha < 0):
  → target 降低 → 相对当前持仓偏"多"
  → 买单 spread 扩大（抑制买入）/ 卖单 spread 收窄（鼓励卖出）
  → 结果：降低库存
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
| `alphaModelType` | — | ML 模型类型 (`random_forest` / `xgboost`) |
| `alphaModelPath` | — | 序列化模型文件路径 |
| `alphaModelName` | default | ML 模型名称（日志标识） |
| `alphaMlWeight` | 0.3 | ML 信号在复合 Alpha 中的权重 |

## 机器学习模型

系统内置纯 Java 实现的随机森林 (Random Forest) 模型，作为 Alpha 信号的一部分参与价差计算。

### 架构

```
MLModel (接口)
 └── RandomForestModel    决策树集成 → 取均值
      └── DecisionTree    二叉树 → JSON 序列化

MLAlphaSignal (AlphaSignal 实现)
 ├── FeatureExtractor   → 提取 10 维特征向量
 └── MLModel.predict()  → 预测值 → tanh 映射到 [-1, 1]
      │
      ▼
AlphaAggregator.registerMLModel() → CompositeAlpha 加权合成
```

### 特征向量

`FeatureExtractor` 从市场数据中提取 10 维特征：

| 索引 | 特征 | 说明 |
|------|------|------|
| 0 | log_return_1 | 1 周期对数收益率 |
| 1 | log_return_3 | 3 周期对数收益率 |
| 2 | log_return_5 | 5 周期对数收益率 |
| 3 | log_return_10 | 10 周期对数收益率 |
| 4 | volatility | 变异系数 (VolatilityTracker) |
| 5 | spread_bps | 买卖价差 (基点) |
| 6 | imbalance | 订单簿不平衡度 [-1, 1] |
| 7 | bid_volume | 买单总深度 (对数) |
| 8 | ask_volume | 卖单总深度 (对数) |
| 9 | price_level | 价格相对 MA(5) 偏离度 |

### 决策树 (DecisionTree)

- 二叉树结构：内部节点按 `featureIndex <= threshold` 分裂，叶子节点存储预测值
- JSON 序列化：手写递归下降解析器，不依赖第三方库
- 支持存储/加载：`toJson()` / `fromJson(String)`

### 随机森林 (RandomForestModel)

- 集成 N 棵决策树，输出为所有树预测均值
- Jackson JSON 序列化：`save(File)` / `load(File)` / `fromJson(String)`
- JSON 格式：`{ name, featureCount, trees: [treeJson, ...] }`
- 可通过文件路径或 classpath 资源加载

### 集成路径

ML 模型在 `SpreadCalculatorFactory` 中注册到 `AlphaAggregator`：

```
SpreadCalculatorFactory.buildCalculator()
  ├── 读取 alphaModelType / alphaModelPath
  ├── RandomForestModel.load(file) → MLModel
  └── alphaAggregator.registerMLModel(symbol, model)
```

### 配置示例

```yaml
strategy:
  spread:
    symbols:
      BTCUSDT:
        alphaEnabled: true
        alphaModelType: random_forest
        alphaModelPath: models/btc_rf_model.json
        alphaModelName: btc_rf_v1
        alphaMlWeight: 0.3
```

### 扩展其他模型

- XGBoost: 引入 `xgboost4j` 依赖，实现 `MLModel` 接口，调用 `XGBoost.predict()`
- LightGBM: 引入 `lightgbmlib` 依赖，实现 `MLModel` 接口
- 模型训练: `ModelTrainer` 可基于历史数据训练随机森林 (TODO)

## 风险控制

策略执行时按以下顺序进行风控检查：

```
StrategyExecutor.execute()
  ├── CircuitBreakerRisk    熔断：连续失败 N 次后冷却
  ├── MaxDrawdownRisk       最大回撤：组合价值回撤超过阈值停止
  ├── MaxOrderCountRisk     订单数量：活跃订单数超限停止
  ├── PriceDeviationRisk    价格偏离：挂单价格偏离市场价过远
  └── OrdersMakerImpl       铺单执行（使用 SpreadCalculator 计算价差）
```

### 风控参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `risk.circuit.breaker.threshold` | 10 | 触发熔断的连续失败次数 |
| `risk.circuit.breaker.cooldown.ms` | 60000 | 熔断冷却时间 (ms) |
| `risk.max.drawdown.percent` | 10.0 | 最大回撤百分比 |
| `risk.price.deviation.percent` | 5.0 | 最大价格偏离百分比 |
| `active.orders.number.limit` | 1500 | 最大活跃订单数 |

## 策略执行流程

每个交易对 (symbol) 有一个独立定时任务线程，按 `updateIntervalMs` 间隔执行：

```
1. 记录中间价 (供 VolatilityTracker 计算波动率)
2. 风控检查：熔断 / 回撤 / 订单数 / 价格偏离
3. CancelOrderBookOutOrder  — 撤销订单簿范围外的挂单
4. UpdateOrderCount         — 更新活跃订单计数
5. MaxDrawdownRisk.update   — 更新组合价值
6. OrdersMakerImpl           — 同步订单簿并铺单
   └─ adjustOrderBook        — 使用 SpreadCalculator 计算价格偏移
   └─ batchCancelOrder       — 批量撤单
   └─ batchPlaceOrder        — 批量下单
```

## 本地开发

```bash
# 编译
mvn compile -pl orderbook-core -am

# 启动 (local profile)
java -Dspring.profiles.active=local \
     -jar orderbook-core/target/orderbook-core.jar \
     --connect.exchange.OSL_GLOBAL.stream_public_use=false \
     --connect.exchange.OSL_GLOBAL.stream_private_use=false
```

注意事项：
- 本地环境不连接 Apollo，配置在 `application-local.yml`
- 不连接交易所 (stream 关闭)，仅验证启动和框架逻辑
- 策略自动启动开关在 `StrategyBootstrap.isAutoStartup()`，当前为 `false`
