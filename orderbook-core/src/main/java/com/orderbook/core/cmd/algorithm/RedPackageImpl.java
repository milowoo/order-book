package com.orderbook.core.cmd.algorithm;

import com.google.common.collect.Lists;
import com.orderbook.cmd.algorithm.RedPackage;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.core.annotation.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Command(name = ExchangeFunc.READ_PACKAGE)
@RequiredArgsConstructor
public class RedPackageImpl implements RedPackage {

    @Override
    public List<Long> call(Map<String, Object> env, ExchangeCode exchangeCode, String money, String num) {
        BigDecimal moneyB = new BigDecimal(money);
        long numL = Long.parseLong(num);
        return divideRedPackage(moneyB.longValue(), (int) numL);
    }

    private static List<Long> divideRedPackage(long money, int people) {
        if (people < 1 || money < people) {
            return Lists.newArrayList();
        }
        List<Long> team = new ArrayList<>();
        List<Long> result = new ArrayList<>();
        if (people == 1) {
            result.add(money);
            return result;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long m = money - 1;
        while (team.size() < people - 1) {
            long nextInt = random.nextLong(m) + 1;
            if (!team.contains(nextInt)) {
                team.add(nextInt);
            }
        }
        Collections.sort(team);
        for (int i = 0; i < team.size(); i++) {
            if (i == 0) {
                result.add(team.get(i));
            } else {
                result.add(team.get(i) - team.get(i - 1));
                if (i == team.size() - 1) {
                    result.add(money - team.get(i));
                }
            }
        }
        if (people == 2) {
            result.add(money - result.get(0));
        }
        return result;
    }
}