package com.orderbook.connector.common.dto;

import com.orderbook.connector.common.dto.DepthPair;
import com.orderbook.connector.common.dto.ModifyDepthResult;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class SpotModifyDepthVo implements Serializable {

    // 挂单错误数量
    private int newErrorCount;

    // 新单错误，按顺序，错误原因，只放前2条
    private List<String> errors = new LinkedList<>();

    // 取消数量
    private int cancelCount;

    // 新单数量
    private int newCount;

    // 容忍未改变数量
    private int tolerateUnChangeCount;

    // 用户未改变数量
    private int userUnChangeCount;

    // 取消错误数量
    private int cancelErrorCount;

    private List<DepthPair> dealtAsks = new LinkedList<>();
    private List<DepthPair> dealtBids = new LinkedList<>();

    public void merge(ModifyDepthResult result) {
        cancelCount = cancelCount + result.getCancelCount();
        newCount = newCount + result.getNewCount();
        tolerateUnChangeCount = tolerateUnChangeCount + result.getTolerateUnChangeCount();
        userUnChangeCount = userUnChangeCount + result.getUserUnChangeCount();
        cancelErrorCount = cancelErrorCount + result.getCancelErrorCount();
        newErrorCount = newErrorCount + result.getNewErrorCount();
        for (String error : result.getErrors()) {
            addError(error);
        }
    }

    // 增加错误，不超过2条
    // Params: error
    public void addError(String error) {
        if (errors.size() >= 2) {
            return;
        }
        errors.add(error);
    }
}