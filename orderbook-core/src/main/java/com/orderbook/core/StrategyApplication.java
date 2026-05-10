package com.orderbook.core;

import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.config.SpreadConfig;
import com.orderbook.core.config.StrategyProps;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan(basePackages = "com.orderbook")
@SpringBootApplication
@EnableScheduling
@MapperScan("com.orderbook.core.mapper")
@EnableConfigurationProperties({ExchangeConnectConfig.class, StrategyProps.class, SpreadConfig.class})
public class StrategyApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyApplication.class, args);
    }
}