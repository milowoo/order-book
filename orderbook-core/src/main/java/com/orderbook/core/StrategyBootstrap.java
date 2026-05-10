package com.orderbook.core;

import com.orderbook.core.component.StrategyExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyBootstrap implements SmartLifecycle {
    private boolean isRunning = false;
    private final StrategyExecutor strategyExecutor;

    @Override
    public void start() {
        this.isRunning = true;
        log.info("==========>trading bot begin !<<<<<<<<<<<<<<<<<");
        this.strategyExecutor.start();
        log.info("==========>trading bot has started!<<<<<<<<<<<<<<<<<");
    }

    @Override
    public void stop() {
        this.isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 是否在项目启动的时候自动启动，测试的时候如果不想执行策略，则返回false，反之改为true
     */
    @Override
    public boolean isAutoStartup() {
        return false;
    }
}