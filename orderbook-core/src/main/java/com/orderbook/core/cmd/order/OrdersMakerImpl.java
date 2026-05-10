package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.OrdersMaker;
import com.orderbook.connector.common.dto.*;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.*;
import com.orderbook.core.service.FeeService;
import com.orderbook.core.sor.SOREngine;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.spread.SpreadCalculator;
import com.orderbook.core.strategy.spread.SpreadCalculatorFactory;
import com.orderbook.core.utils.MathUtils;
import com.orderbook.core.utils.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.ORDERS_MAKER)
@RequiredArgsConstructor
public class OrdersMakerImpl extends AbstractOrdersCmd implements OrdersMaker {

    @Autowired
    private OrderBookStore orderBookStore;
    @Autowired
    private OpenOrdersStore openOrdersStore;
    @Autowired
    private SpreadCalculatorFactory spreadCalculatorFactory;
    @Autowired
    private FeeService feeService;
    @Autowired
    private SOREngine sorEngine;

    // Arbitrage signal from strategy: factor [0,1] to tighten spread 来自策略的套利信号：一个 [0,1] 区间的因子，用于收窄价差
    // Set via env in call(), consumed in adjustOrderBook() 在 call() 方法中通过环境变量设置，并在 adjustOrderBook() 方法中被消费使用
    private volatile BigDecimal arbitrageTightenFactor = BigDecimal.ONE;

    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        long beginTime = System.currentTimeMillis();
        try {
            SymbolBo symbolBo = symbolStore.findSymbolById(symbol);
            if (symbolBo == null) return false;

            // Read arbitrage signal from env (if any) 从环境变量中读取套利信号（如果存在的话）
            updateArbitrageSignal(env);

            OrderBook bybitOrderBook = orderBookStore.get(ExchangeCode.BYBIT, symbolBo.getSymbolId());
            if (!isValidOrderBook(bybitOrderBook)) return false;

            // 对bybit的订单簿进行调整
            adjustOrderBook(bybitOrderBook, symbolBo);

            OrderBook orderBook = orderBookStore.get(exchangeCode, symbolBo.getSymbolId());
            if (orderBook == null) return false;

            // Resolve target exchange via SOR (if enabled) 通过智能订单路由系统确定目标交易所（如果该功能已启用）
            ExchangeCode targetExchange = resolveExchange(symbolBo, exchangeCode);

            // 获取用户的活跃订单（从本地缓存读取，避免每 tick 的 REST 调用）
            OpenOrdersBo openOrdersBo = openOrdersStore.getCachedOpenOrders(exchangeCode, symbolBo.getSymbolId());
            // 拷贝一份副本，防止并发修改
            Map<Long, OrderBo> orderBoMap = new HashMap<>(openOrdersBo.getOrders());

            // 订单数量超过限制，不再铺单
            if (orderBoMap.size() > apolloConfig.getActiveOrderNumberLimit()) {
                return false;
            }

            // 初始化订单簿的场景
            if (isFirstOrderPlacement(orderBook)) {
                // Bybit有、本地无 → 补单 -- 首次铺单
                adjustQuantities(bybitOrderBook.getAsk(), symbolBo);
                batchPlaceOrder(targetExchange, symbolBo, "sell", convertSpotOrders(bybitOrderBook.getAsk(), symbolBo, "sell"));
                adjustQuantities(bybitOrderBook.getBid(), symbolBo);
                batchPlaceOrder(targetExchange, symbolBo, "buy", convertSpotOrders(bybitOrderBook.getBid(), symbolBo, "buy"));
                return true;
            }

            long processOnlyInOSLTime = System.currentTimeMillis();
            // 本地有、Bybit无 → 撤单
            List<OrderBo> askCancelOrders = processOnlyInOSL(bybitOrderBook, orderBook, symbolBo, orderBoMap, "sell");
            List<OrderBo> bidCancelOrders = processOnlyInOSL(bybitOrderBook, orderBook, symbolBo, orderBoMap, "buy");
            log.info("processOnlyInOSL use time {}", System.currentTimeMillis() - processOnlyInOSLTime);

            // Bybit有、本地无 → 补单
            long processOnlyInBybitTime = System.currentTimeMillis();
            processOnlyInBybit(bybitOrderBook, orderBook, symbolBo, targetExchange);
            log.info("processOnlyInBybit use time {}", System.currentTimeMillis() - processOnlyInBybitTime);

            long processOrdersSellTime = System.currentTimeMillis();
            // 铺单逻辑（卖单 / 买单）
            processOrders(
                    Optional.ofNullable(orderBook.getAsk()).orElse(Collections.emptyList()),
                    bybitOrderBook.getAsk(),
                    symbolBo,
                    orderBoMap,
                    "sell",
                    targetExchange
            );
            // 先下单，再撤单，保证盘口不会清空的场景
            batchCancelOrder(targetExchange, symbolBo, askCancelOrders, orderBoMap);
            log.info("processOrdersSellTime use time {}", System.currentTimeMillis() - processOrdersSellTime);

            long processOrdersBuyTime = System.currentTimeMillis();
            processOrders(
                    Optional.ofNullable(orderBook.getBid()).orElse(Collections.emptyList()),
                    bybitOrderBook.getBid(),
                    symbolBo,
                    orderBoMap,
                    "buy",
                    targetExchange
            );
            batchCancelOrder(targetExchange, symbolBo, bidCancelOrders, orderBoMap);
            log.info("processOrdersBuyTime use time {}", System.currentTimeMillis() - processOrdersBuyTime);

            return true;
        } catch (Exception e) {
            log.error("OrdersMakerImpl exception:", e);
            return false;
        } finally {
            log.info("OrdersMakerImpl cost:{} ms", System.currentTimeMillis() - beginTime);
        }
    }

    private boolean isValidOrderBook(OrderBook orderBook) {
        if (orderBook == null) return false;
        if (orderBook.getBid().isEmpty() || orderBook.getAsk().isEmpty()) return false;
        if (orderBook.getBid().size() <= 30 && orderBook.getAsk().size() <= 30) return false;
        return true;
    }

    // 判断是否是首次铺单
    private boolean isFirstOrderPlacement(OrderBook orderBook) {
        if (orderBook.getAsk() == null || orderBook.getBid() == null) {
            return true;
        }
        if (orderBook.getAsk().size() <= 10 && orderBook.getBid().size() <= 10) {
            return true;
        }
        return false;
    }

    // 对bybit的订单簿进行价格浮动调整：卖单价格上升（price + ratio），买单价格下降（乘以1+ratio）
    private void adjustOrderBook(OrderBook orderBook, SymbolBo symbolBo) {
        if (orderBook == null || orderBook.getBid() == null || orderBook.getAsk() == null) return;

        int limitLevel = Math.max(apolloConfig.getOrderBookLimitLevel(), 15);
        // 截断 ask 列表（如果 ask 档位过多）
        if (orderBook.getAsk() != null && orderBook.getAsk().size() > limitLevel) {
            List<PriceLevel> trimmedAsks = orderBook.getAsk().subList(0, limitLevel);
            orderBook.setAsk(new ArrayList<>(trimmedAsks));
        }
        // 截断 bid 列表（如果 bid 档位不足）
        if (orderBook.getBid() != null && orderBook.getBid().size() > limitLevel) {
            List<PriceLevel> trimmedBids = orderBook.getBid().subList(0, limitLevel);
            orderBook.setBid(new ArrayList<>(trimmedBids));
        }

        // 使用 SpreadCalculator 计算偏移量
        SpreadCalculator calculator = spreadCalculatorFactory.getCalculator(symbolBo.getSymbol());
        BigDecimal askOffset = calculator.calculateOffset(symbolBo.getSymbol(), false, symbolBo);
        BigDecimal bidOffset = calculator.calculateOffset(symbolBo.getSymbol(), true, symbolBo);

        // Clamp offsets to break-even spread (minimum spread to cover taker fees)
        BigDecimal refPrice = getReferencePrice(orderBook);
        if (refPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal breakEvenSpread = feeService.calculateBreakEvenSpread(
                    ExchangeCode.BYBIT, symbolBo.getSymbol(), refPrice);
            BigDecimal minHalfSpread = breakEvenSpread.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            if (askOffset.compareTo(minHalfSpread) < 0) {
                askOffset = minHalfSpread;
            }
            if (bidOffset.compareTo(minHalfSpread) < 0) {
                bidOffset = minHalfSpread;
            }
        }

        // Apply arbitrage tighten factor (tighten spread to capture cross-exchange profit)
        BigDecimal factor = this.arbitrageTightenFactor;
        if (factor.compareTo(BigDecimal.ONE) < 0) {
            askOffset = askOffset.multiply(factor);
            bidOffset = bidOffset.multiply(factor);
            if (log.isDebugEnabled()) {
                log.debug("[{}] Arbitrage tighten factor={} -> askOffset={} bidOffset={}",
                        symbolBo.getSymbol(), factor, askOffset, bidOffset);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("[{}] Spread '{}' askOffset={} bidOffset={}",
                    symbolBo.getSymbol(), calculator.getName(), askOffset, bidOffset);
        }

        // 卖单价格上升
        for (PriceLevel priceLevel : orderBook.getAsk()) {
            priceLevel.setPrice(priceLevel.getPrice().add(askOffset));
        }
        // 买单价格下降
        for (PriceLevel priceLevel : orderBook.getBid()) {
            priceLevel.setPrice(priceLevel.getPrice().subtract(bidOffset));
        }
    }

    // 本地有, bybit没有 → 撤单
    private List<OrderBo> processOnlyInOSL(OrderBook bybitOrderBook, OrderBook orderBook, SymbolBo symbolBo, Map<Long, OrderBo> orderBoMap, String side) {
        List<OrderBo> needCancelOrders = new ArrayList<>();
        boolean isSell = "sell".equalsIgnoreCase(side);
        List<PriceLevel> bybitLevels = isSell ? bybitOrderBook.getAsk() : bybitOrderBook.getBid();
        List<PriceLevel> localLevels = isSell ? orderBook.getAsk() : orderBook.getBid();

        // 查找只在本地有、Bybit没有的档位
        List<PriceLevel> onlyInOSL = findOnlyInOSL(symbolBo, bybitLevels, localLevels);
        for (PriceLevel priceLevel : onlyInOSL) {
            List<OrderBo> orders = getOrderByPrice(side.toLowerCase(), priceLevel.getPrice(), orderBoMap);
            needCancelOrders.addAll(orders);
        }
        return needCancelOrders;
    }

    // 处理bybit订单簿有，本地没有 → 铺单
    private void processOnlyInBybit(OrderBook bybitOrderBook, OrderBook orderBook, SymbolBo symbolBo, ExchangeCode exchange) {
        List<PriceLevel> askLevels = findOnlyInBybit(
                symbolBo,
                bybitOrderBook.getAsk(),
                Optional.ofNullable(orderBook.getAsk()).orElse(Collections.emptyList())
        );
        List<PriceLevel> bidLevels = findOnlyInBybit(
                symbolBo,
                bybitOrderBook.getBid(),
                Optional.ofNullable(orderBook.getBid()).orElse(Collections.emptyList())
        );
        log.info("processOnlyInBybit ask size {} bid size {}", askLevels.size(), bidLevels.size());

        adjustQuantities(askLevels, symbolBo);
        adjustQuantities(bidLevels, symbolBo);

        batchPlaceOrder(exchange, symbolBo, "sell", convertSpotOrders(askLevels, symbolBo, "sell"));
        batchPlaceOrder(exchange, symbolBo, "buy", convertSpotOrders(bidLevels, symbolBo, "buy"));
    }

    /**
     * 调整每个价格档位的数量
     */
    private void adjustQuantities(List<PriceLevel> levels, SymbolBo symbolBo) {
        for (PriceLevel priceLevel : levels) {
            BigDecimal oldQty = priceLevel.getQuantity();
            BigDecimal rate = MathUtils.randomBetween(symbolBo.getMinRate(), symbolBo.getMaxRate());
            BigDecimal newQty = oldQty.multiply(rate);

            // 限制最大委托数量
            if (newQty.compareTo(symbolBo.getMaxDelegateCount()) >= 0) {
                newQty = symbolBo.getMaxDelegateCount()
                        .subtract(MathUtils.randomBetween(symbolBo.getMinSize(), symbolBo.getMaxSize()));
            }
            // 保证最小挂单数量
            if (newQty.compareTo(symbolBo.getMinSize()) <= 0) {
                newQty = symbolBo.getMinSize();
            }
            newQty = SymbolUtils.validateSize(newQty, symbolBo.getMinSize());
            priceLevel.setQuantity(newQty);
        }
    }

    private List<SpotOrdersV2Req> convertSpotOrders(List<PriceLevel> levels, SymbolBo symbolBo, String side) {
        return levels.stream()
                .map(level -> {
                    SpotOrdersV2Req req = new SpotOrdersV2Req();
                    req.setSide(side);
                    req.setPrice(level.getPrice().toPlainString());
                    req.setSize(level.getQuantity().toPlainString());
                    return req;
                })
                .collect(Collectors.toList());
    }

    /**
     * 找出bybit中有，但本地订单簿中没有的价格档位。
     *
     * @param bybitLevels bybit的订单簿
     * @param localLevels   本地订单簿
     * @return 仅在bybit中存在的PriceLevel列表
     */
    private List<PriceLevel> findOnlyInBybit(SymbolBo symbolBo, List<PriceLevel> bybitLevels, List<PriceLevel> oslLevels) {
        if (bybitLevels == null || oslLevels == null || symbolBo == null || symbolBo.getTickSize() == null) {
            return Collections.emptyList();
        }

        BigDecimal tickSize = symbolBo.getTickSize();
        int scale = tickSize.scale();
        // 将本地价格格式化为统一精度的字符串集合
        Set<String> oslPriceStr = new HashSet<>(oslLevels.size());
        for (PriceLevel level : oslLevels) {
            oslPriceStr.add(level.getPrice().setScale(scale, RoundingMode.HALF_UP).toPlainString());
        }

        // 过滤出 bybit 中本地没有的价格档
        List<PriceLevel> result = new ArrayList<>();
        for (PriceLevel level : bybitLevels) {
            String formattedPrice = level.getPrice().setScale(scale, RoundingMode.HALF_UP).toPlainString();
            if (!oslPriceStr.contains(formattedPrice)) {
                result.add(level);
            }
        }
        return result;
    }

    /**
     * 根据side&price找出匹配的订单
     *
     * @param side      方向
     * @param price     价格
     * @param orderBoMap 本地挂单映射
     * @return 该价格对应的订单列表
     */
    private List<OrderBo> getOrderByPrice(String side, BigDecimal price, Map<Long, OrderBo> orderBoMap) {
        // 根据 side & price 确定过滤器
        Predicate<OrderBo> sideFilter = "buy".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        return orderBoMap.values().stream()
                .filter(sideFilter)
                .filter(order -> order.getPrice().compareTo(price) == 0)
                .collect(Collectors.toList());
    }

    /**
     * 找出本地存在，但 bybit 中没有的价格档位
     */
    private List<PriceLevel> findOnlyInOSL(SymbolBo symbolBo, List<PriceLevel> bybitLevels, List<PriceLevel> oslLevels) {
        if (bybitLevels == null || oslLevels == null || symbolBo == null || symbolBo.getTickSize() == null) {
            return Collections.emptyList();
        }
        BigDecimal tickSize = symbolBo.getTickSize();
        int scale = tickSize.scale();
        // 使用字符串集合避免 BigDecimal 精度对比问题
        Set<String> bybitPriceStr = new HashSet<>(bybitLevels.size());
        for (PriceLevel level : bybitLevels) {
            bybitPriceStr.add(level.getPrice().setScale(scale, RoundingMode.HALF_UP).toPlainString());
        }

        List<PriceLevel> result = new ArrayList<>();
        for (PriceLevel level : oslLevels) {
            String formattedPrice = level.getPrice().setScale(scale, RoundingMode.HALF_UP).toPlainString();
            if (!bybitPriceStr.contains(formattedPrice)) {
                result.add(level);
            }
        }
        return result;
    }

    /**
     * 通用处理逻辑：找出本地订单簿中超出补偿区间的订单，进行撤单并重新铺单。
     *
     * @param localLevels   本地订单簿 (ask 或 bid)
     * @param bybitLevels Bybit 的订单簿 (作为基准)
     * @param symbolBo    交易对配置
     * @param orderBoMap  本地挂单缓存
     * @param side        买卖方向: "buy" 或 "sell"
     */
    private void processOrders(List<PriceLevel> oslLevels, List<PriceLevel> bybitLevels, SymbolBo symbolBo, Map<Long, OrderBo> orderBoMap, String side, ExchangeCode exchange) {
        if (symbolBo == null || symbolBo.getTickSize() == null) {
            return;
        }

        int scale = symbolBo.getTickSize().scale(); // 统一精度
        // 构建 bybit 价格映射（格式化为统一精度的字符串）
        Map<String, BigDecimal> bybitPriceMap = bybitLevels.stream()
                .collect(Collectors.toMap(
                        priceLevel -> priceLevel.getPrice().setScale(scale, RoundingMode.HALF_UP).toPlainString(),
                        PriceLevel::getQuantity
                ));

        List<OrderBo> ordersToCancel = new ArrayList<>();
        Map<BigDecimal, BigDecimal> compensationPrices = new HashMap<>();

        for (PriceLevel oslLevel : oslLevels) {
            BigDecimal price = oslLevel.getPrice();
            String priceStr = price.setScale(scale, RoundingMode.HALF_UP).toPlainString();
            BigDecimal oslQty = oslLevel.getQuantity();

            BigDecimal bybitQty = bybitPriceMap.get(priceStr);
            if (bybitQty == null || oslQty.compareTo(bybitQty) == 0) {
                continue; // bybit 没有该价位 或 数量一致，无需处理
            }

            BigDecimal minAllowed = bybitQty.multiply(symbolBo.getMinRate());
            BigDecimal maxAllowed = bybitQty.multiply(symbolBo.getMaxRate());
            if (oslQty.compareTo(minAllowed) >= 0 && oslQty.compareTo(maxAllowed) <= 0) {
                continue; // 在容忍区间内
            }

            ordersToCancel.addAll(getOrderByPrice(side, price, orderBoMap));
            // 防止大单
            if (oslQty.compareTo(symbolBo.getMaxDelegateCount()) >= 0) {
                oslQty = symbolBo.getMaxDelegateCount()
                        .subtract(MathUtils.randomBetween(symbolBo.getMinSize(), symbolBo.getMaxSize()));
            }
            oslQty = SymbolUtils.validateSize(oslQty, symbolBo.getMinSize());
            compensationPrices.put(price, oslQty);
        }

        // 批量撤单
        batchCancelOrder(exchange, symbolBo, ordersToCancel, orderBoMap);

        List<PriceLevel> orders = new ArrayList<>();
        for (Map.Entry<BigDecimal, BigDecimal> entry : compensationPrices.entrySet()) {
            BigDecimal compPrice = entry.getKey();
            BigDecimal compQty = entry.getValue();

            BigDecimal origQty = getOrderPriceQty(orderBoMap, compPrice);
            BigDecimal newQty = compQty.subtract(origQty);

            BigDecimal rate = MathUtils.randomBetween(symbolBo.getMinRate(), symbolBo.getMaxRate());
            BigDecimal numQty = compQty.multiply(rate);
            numQty = SymbolUtils.validateSize(numQty, symbolBo.getMinSize());

            if (numQty.compareTo(symbolBo.getMinSize()) <= 0) {
                numQty = symbolBo.getMinSize();
            }

            PriceLevel priceLevel = new PriceLevel();
            priceLevel.setPrice(compPrice.setScale(scale, RoundingMode.HALF_UP));
            priceLevel.setQuantity(numQty);
            orders.add(priceLevel);
        }

        List<SpotOrdersV2Req> allList = convertSpotOrders(orders, side);
        batchPlaceOrder(exchange, symbolBo, side, allList);
    }

    /**
     * Resolve target exchange for order placement using SOR if enabled.
     */
    private ExchangeCode resolveExchange(SymbolBo symbolBo, ExchangeCode defaultExchange) {
        if (apolloConfig.getSOREnabled()) {
            return sorEngine.selectExchange(symbolBo.getSymbolId(), null, null, null);
        }
        return defaultExchange;
    }

    /**
     * Read arbitrage signals from env and set the tighten factor.
     * Called at the start of each call().
     */
    private void updateArbitrageSignal(Map<String, Object> env) {
        // Reset to default (no tightening)
        this.arbitrageTightenFactor = BigDecimal.ONE;

        try {
            if (env == null || !env.containsKey("arb_net_profit")) return;
            if (!"true".equals(env.get("arb_executable"))) return;

            BigDecimal netProfit = new BigDecimal(env.get("arb_net_profit").toString());
            if (netProfit.compareTo(BigDecimal.ZERO) <= 0) return;

            // Tighten spread proportionally to the arbitrage profit
            // Higher profit = tighter spread (factor closer to 0)
            // Cap at 50% tightening (factor >= 0.5)
            double tighten = Math.max(0.5, 1.0 - netProfit.doubleValue() * 0.01);
            this.arbitrageTightenFactor = BigDecimal.valueOf(tighten);

            log.info("[Arbitrage] Signal profit={}, tighten factor={}", netProfit, this.arbitrageTightenFactor);
        } catch (Exception e) {
            log.warn("[Arbitrage] Failed to parse signal", e);
            this.arbitrageTightenFactor = BigDecimal.ONE;
        }
    }

    /**
     * Get reference price from order book mid-price.
     */
    private BigDecimal getReferencePrice(OrderBook orderBook) {
        if (orderBook == null || orderBook.getBid() == null || orderBook.getAsk() == null
                || orderBook.getBid().isEmpty() || orderBook.getAsk().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return orderBook.getBestBidPrice()
                .add(orderBook.getBestAskPrice())
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }

    /**
     * 获取指定价格的订单数量（如果存在），否则返回 0。
     *
     * @param orderBoMap 所有订单的映射
     * @param price      要查找的价格
     * @return 该价格对应的订单数量, 如果不存在则返回 BigDecimal.ZERO
     */
    private BigDecimal getOrderPriceQty(Map<Long, OrderBo> orderBoMap, BigDecimal price) {
        return orderBoMap.values().stream()
                .filter(order -> order.getPrice().compareTo(price) == 0)
                .map(OrderBo::getQuantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}