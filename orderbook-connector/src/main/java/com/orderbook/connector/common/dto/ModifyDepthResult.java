package com.orderbook.connector.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

@Data
public class ModifyDepthResult implements Serializable {

    private static final long serialVersionUID = -7154274768146127243L;
    private int cancelCount;
    private int newCount;
    private int tolerateUnChangeCount;
    private int userUnChangeCount;
    private int cancelErrorCount;
    private int newErrorCount;
    private List<String> errors = new LinkedList<>();

    public void merge(ModifyDepthResult result) {
        this.cancelCount += result.getCancelCount();
        this.newCount += result.getNewCount();
        this.tolerateUnChangeCount += result.getTolerateUnChangeCount();
        this.userUnChangeCount += result.getUserUnChangeCount();
        this.cancelErrorCount += result.getCancelErrorCount();
        this.newErrorCount += result.getNewErrorCount();

        for (String error : result.getErrors()) {
            this.addError(error);
        }
    }

    public void addError(String error) {
        if (this.errors.size() < 2) {
            this.errors.add(error);
        }
    }
}