package com.orderbook.cmd;

import java.util.List;
import java.util.Map;

public interface StrategyCmd {

    default Object call(Map<String, Object> env, List<Object> params) {
        throw new UnsupportedOperationException();
    }

    default Object call(Map<String, Object> env, ExchangeCode exchangeCode) {
        throw new UnsupportedOperationException();
    }

    default Object call(Map<String, Object> env, ExchangeCode exchangeCode, String arg1) {
        throw new UnsupportedOperationException();
    }

    default Object call(Map<String, Object> env, ExchangeCode exchangeCode, String arg1, String arg2) {
        throw new UnsupportedOperationException();
    }

    default Object call(Map<String, Object> env, ExchangeCode exchangeCode, String arg1, String arg2, String arg3) {
        throw new UnsupportedOperationException();
    }

    default Object call(Map<String, Object> env, ExchangeCode exchangeCode, String arg1, String arg2, String arg3, String arg4) {
        throw new UnsupportedOperationException();
    }

    default Object call(Map<String, Object> env, ExchangeCode exchangeCode, String arg1, String arg2, String arg3, String arg4, String arg5) {
        throw new UnsupportedOperationException();
    }
    default Object call(Map<String, Object> env, ExchangeCode exchangeCode, String arg1, String arg2, String arg3, String arg4, String arg5, String arg6) {
        throw new UnsupportedOperationException();
    }
}