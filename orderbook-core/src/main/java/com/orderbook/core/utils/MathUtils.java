package com.orderbook.core.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

    public static BigDecimal randomBetween(BigDecimal minRate, BigDecimal maxRate) {
        return randomRateBetween(minRate, maxRate);
    }

    public static BigDecimal randomRateBetween(BigDecimal minRate, BigDecimal maxRate) {
        if (minRate == null || maxRate == null || minRate.compareTo(maxRate) >= 0) {
            throw new IllegalArgumentException("Invalid rate bounds");
        }
        // 将 min/max 转为 double 用于随机计算
        double min = minRate.doubleValue();
        double max = maxRate.doubleValue();

        // 生成随机 double 值
        double randomValue = ThreadLocalRandom.current().nextDouble(min, max);

        // 转回 BigDecimal，保留8位小数（可根据需要调整）
        return BigDecimal.valueOf(randomValue).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 随机生成数字
     * Params: rand
     * Returns:
     */
    public static int random(int rand) {
        if (rand <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(rand);
    }
}