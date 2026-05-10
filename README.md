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
      ETHUSDT:
        model: constant
        baseOffsetTicks: 1.0
```

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
