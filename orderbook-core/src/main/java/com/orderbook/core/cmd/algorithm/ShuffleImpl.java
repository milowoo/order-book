package com.orderbook.core.cmd.algorithm;

import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.algorithm.Shuffle;
import com.orderbook.core.annotation.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.SHUFFLE_ALGORITHM)
@RequiredArgsConstructor
public class ShuffleImpl implements Shuffle {

    @Override
    public List<Object> call(Map<String, Object> env, List<Object> params) {
        Collections.shuffle(params);
        return params;
    }
}