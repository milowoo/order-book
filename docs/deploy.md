# 部署文档

## 环境依赖

| 组件 | 版本要求 | 用途 |
|------|---------|------|
| JDK | 21 | 运行环境 |
| MySQL / TiDB | 5.7+ / 5.4+ | 订单持久化、模型训练数据存储 |
| Redis | 6.x+ | 订单簿快照、填充缓存 |
| Apollo Config | 2.x+ | 配置中心（可选，本地开发可跳过） |
| Prometheus | 任意 | 指标采集（可选） |
| Grafana | 任意 | 监控仪表盘（可选） |

### 端口说明

| 端口 | 用途 | 说明 |
|------|------|------|
| 8080 | Spring Boot 应用 | HTTP API + Actuator |
| 9090 | Prometheus | /actuator/prometheus 端点 |

---

## 构建

```bash
# 全量编译
mvn clean compile -pl orderbook-core -am

# 打包可执行 JAR
mvn clean package -pl orderbook-core -am -DskipTests

# 编译并运行测试
mvn test -pl orderbook-core -am
```

构建产物：`orderbook-core/target/orderbook-core.jar`

---

## 数据库初始化

系统依赖 MySQL/TiDB 存储交易数据、模型训练数据和订单历史。首次部署需执行 schema 初始化：

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS orderbook;
```

schema 文件位于 `orderbook-core/src/main/resources/schema.sql`，Spring Boot 启动时自动执行。

如果关闭自动初始化，需手动执行：

```bash
mysql -h <host> -P <port> -u <user> -p orderbook < orderbook-core/src/main/resources/schema.sql
```

---

## 配置管理

### 配置优先级

系统支持两套配置源，按优先级：

1. **Apollo 配置中心**（生产推荐）— 动态配置，运行时热更新
2. **application-local.yml**（本地开发）— 固定配置，修改后需重启

### Apollo 配置 （生产）

在 Apollo 中创建 `orderbook-core` 命名空间，配置项前缀见 `ApolloConfig.java`。

### 本地配置 （local profile）

配置文件：`orderbook-core/src/main/resources/application-local.yml`

```yaml
# 交易所配置
connect:
  exchange:
    BYBIT:
      stream_public_use: true
      stream_private_use: false
    OSL_GLOBAL:
      stream_public_use: true
      stream_private_use: true

# 数据源
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/orderbook
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379

# 价差与 Alpha 配置
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

# 套利配置
arbitrage:
  enabled: true
  minProfitUsdt: 0.5
  maxOrderQty: 1
```

### 关键配置项

全部配置参数参见 `ApolloConfig.java`，关键参数速查：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `max.place.order.limit` | 10 | 单次批量下单最大数量 |
| `active.orders.number.limit` | 1500 | 全局活跃订单数上限 |
| `risk.circuit.breaker.threshold` | 10 | 熔断阈值（连续失败次数） |
| `risk.circuit.breaker.cooldown.ms` | 60000 | 熔断冷却时间 |
| `risk.max.drawdown.percent` | 10.0 | 最大回撤百分比 |
| `arbitrage.minProfitUsdt` | 0.5 | 套利触发最小利润 |

---

## 启动

### 本地开发

```bash
java -Dspring.profiles.active=local \
     -jar orderbook-core/target/orderbook-core.jar
```

注意事项：
- 本地环境不连接 Apollo，配置在 `application-local.yml`
- Bybit 行情流默认开启，接收参考订单簿数据
- 策略自动启动开关在 `StrategyBootstrap.isAutoStartup()`，当前默认为 `false`（首次需手动确认策略参数后设为 `true`）

### 生产启动

```bash
java -Xms4g -Xmx4g \
     -XX:+UseZGC -XX:MaxGCPauseMillis=5 \
     -XX:+ZGenerational \
     -Dlombok.disableUnsafeWarning=true \
     -Dspring.profiles.active=prod \
     -Dapollo.meta=http://apollo-config:8080 \
     -jar orderbook-core/target/orderbook-core.jar
```

JVM 参数建议（JDK 21 优化）：

| 参数 | 建议值 | 说明 |
|------|--------|------|
| `-Xms` / `-Xmx` | 4g-8g | 堆内存，取决于管理的 symbol 数量 |
| GC 策略 | ZGC (`-XX:+UseZGC`) | JDK 21 推荐，亚毫秒级暂停，适合低延迟场景 |
| `-XX:+ZGenerational` | 启用 | ZGC 分代模式（JDK 21+ 默认），降低 GC CPU 开销 |
| `-XX:MaxGCPauseMillis` | 5 | GC 暂停目标（ZGC 可低至 1-5ms） |
| `-Dspring.threads.virtual.enabled` | true | （已在 application.yml 配置）启用虚拟线程 |
| `-Dlombok.disableUnsafeWarning` | true | 屏蔽 Lombok 在 JDK 21 上的 Unsafe 弃用警告 |
| `-Dspring.profiles.active` | prod | 激活的生产配置 profile |
| `-Dapollo.meta` | — | Apollo 配置中心地址 |

> 💡 **GC 选择建议**：ZGC 在 JDK 21 已生产就绪，暂停时间不受堆大小影响，适合延迟敏感的高频做市场景。如果更关注吞吐量，可继续使用 G1 (`-XX:+UseG1GC`)，配合 `-XX:MaxGCPauseMillis=50`。

---

## 数据库表结构

### 业务表

| 表名 | 用途 |
|------|------|
| `order_book_snapshot` | 订单簿快照持久化 |
| `trade_record` | 成交记录 |
| `train_dataset` | ML 训练数据（特征 + 标签） |
| `model_version` | 已训练模型版本管理 |

### schema.sql

`orderbook-core/src/main/resources/schema.sql` 包含全部建表语句，Spring Boot 启动时自动执行。手动执行：

```bash
mysql -h <host> -u <user> -p orderbook < orderbook-core/src/main/resources/schema.sql
```

---

## 监控

### Prometheus 指标端点

`/actuator/prometheus`

暴露的关键指标：

| 指标 | 类型 | 说明 |
|------|------|------|
| `orderbook_pnl_realized` | Gauge | 已实现盈亏 |
| `orderbook_pnl_unrealized` | Gauge | 未实现盈亏 |
| `orderbook_position_net` | Gauge | 净持仓汇总 |
| `orderbook_connection_healthy` | Gauge | 各交易所连接状态 |
| `orderbook_strategy_latency` | Timer | 策略执行延迟 |
| `orderbook_portfolio_value` | Gauge | 组合总净值 |
| `orderbook_portfolio_drawdown_pct` | Gauge | 组合回撤比例 |
| `orderbook_portfolio_var_95` | Gauge | VaR(95) |
| `orderbook_portfolio_sharpe_ratio` | Gauge | 年化夏普比 |
| `orderbook_portfolio_concentration` | Gauge | 单币集中度 |
| `orderbook_sor_routing_total` | Gauge | SOR 路由计数 |

### Prometheus 采集配置

```yaml
scrape_configs:
  - job_name: 'orderbook'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### Grafana

推荐面板：
- **PnL 面板**：已实现 + 未实现盈亏曲线
- **订单簿健康度**：连接状态、stale 标记
- **风控面板**：VaR、回撤、集中度
- **策略延迟**：P50 / P95 / P99 热力图

---

## 生产上线清单

### 前置检查

- [ ] JDK 21 安装，`java -version` 确认
- [ ] MySQL/TiDB 可用，schema 已初始化
- [ ] Redis 可用
- [ ] Apollo 配置中心已创建命名空间（生产）
- [ ] 交易所 API Key/Secret 已配置（OSL_GLOBAL 需 private stream 权限）
- [ ] JVM 参数已根据机器规格调整
- [ ] 虚拟线程兼容性确认：代码中无 `synchronized` 块与 `ThreadLocal` 耦合（虚拟线程可携带 ThreadLocal，但池化场景需注意）

### 启动前验证

- [ ] `mvn test -pl orderbook-core -am` 全部通过
- [ ] 熔断阈值、回撤百分比等风控参数已核对
- [ ] 各交易所行情 WebSocket 地址可达
- [ ] 策略自动启动设为 `true` 或确认手动启动

### 启动后验证

- [ ] `/actuator/health` 返回 UP
- [ ] 交易所 WebSocket 连接已建立（日志中无 `Connection unhealthy` 警告）
- [ ] Prometheus 指标端点在 Grafana 中可查
- [ ] 订单簿数据正常流入（`OrderBookStore` 中可见 bid/ask 数据）
- [ ] 策略在首个 tick 执行 `CancelOrderBookOutOrder`（日志可见）

### 回滚

```bash
# 停止当前进程
kill <pid>

# 启动上一版本
java -jar orderbook-core-<previous-version>.jar
```

配置回滚：在 Apollo 中恢复上一版本的配置值。

---

## 常见问题

### 启动时连接 DB 失败

检查 `spring.datasource.url` 中 MySQL 地址和端口是否正确，确认 TiDB/MySQL 服务正常运行。

### 策略不执行

确认 `StrategyBootstrap.isAutoStartup()` 返回 `true`。默认 `false` 是为了防止未配置完成的策略意外启动。

### WebSocket 频繁断开

检查网络稳定性。系统内置 `BitgetHandler.checkConnection()` 每 30 秒检查连接健康，断开时自动重连并标记 order book 为 stale。重连后数据会在下一个 checksum 验证通过后自动恢复。
