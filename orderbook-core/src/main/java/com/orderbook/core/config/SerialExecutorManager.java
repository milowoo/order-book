package com.orderbook.core.config;

import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialExecutorManager {
    private static final Logger log = LoggerFactory.getLogger(SerialExecutorManager.class);

    // 买单串行执行器
    private final Map<String, ExecutorService> executorBuyMap = new ConcurrentHashMap<>();
    // 卖单串行执行器
    private final Map<String, ExecutorService> executorSellMap = new ConcurrentHashMap<>();
    // 撤单串行执行器
    private final Map<String, ExecutorService> executorCancelMap = new ConcurrentHashMap<>();

    /**
     * 提交买/卖单任务
     */
    public void submit(String symbolId, String side, Runnable task) {
        Map<String, ExecutorService> map = (side.equalsIgnoreCase("buy")) ? executorBuyMap : executorSellMap;
        submitToMap(symbolId, task, map, side.toLowerCase());
    }

    /**
     * 提交撤单任务
     */
    public void submitCancel(String symbolId, Runnable task) {
        submitToMap(symbolId, task, executorCancelMap, "cancel");
    }

    /**
     * 通用提交逻辑
     */
    private void submitToMap(String symbolId, Runnable task, Map<String, ExecutorService> map, String prefix) {
        ExecutorService executor = map.computeIfAbsent(symbolId, key -> createSingleThreadExecutor(prefix, symbolId));
        executor.submit(task);
    }

    /**
     * 创建命名线程工厂
     */
    private ExecutorService createSingleThreadExecutor(String prefix, String symbolId) {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("serial-executor-" + prefix + "-" + symbolId);
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    /**
     * 关闭所有执行器
     */
    public void shutdownAll() {
        shutdownMap(executorBuyMap, "buy");
        shutdownMap(executorSellMap, "sell");
        shutdownMap(executorCancelMap, "cancel");
    }

    /**
     * 通用关闭逻辑
     */
    private void shutdownMap(Map<String, ExecutorService> map, String type) {
        for (Map.Entry<String, ExecutorService> entry : map.entrySet()) {
            String symbol = entry.getKey();
            ExecutorService executor = entry.getValue();
            try {
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("Force shutdown {} executor for symbol: {}", type, symbol);
                } else {
                    log.info("Gracefully shutdown {} executor for symbol: {}", type, symbol);
                }
            } catch (Exception e) {
                log.error("Failed to shutdown {} executor for symbol: {}", type, symbol, e);
            }
        }
    }

    // 获取活跃执行器（用于监控）
    public Map<String, ExecutorService> getActiveBuyExecutors() {
        return executorBuyMap;
    }

    public Map<String, ExecutorService> getActiveSellExecutors() {
        return executorSellMap;
    }

    public Map<String, ExecutorService> getActiveCancelExecutors() {
        return executorCancelMap;
    }
}