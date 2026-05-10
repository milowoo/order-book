package com.orderbook.core.domain;

import com.orderbook.core.constants.Constant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FillRedisBo {

    private String tradeId;
    private String status;
    private String message;

    public static FillRedisBo create(String tradeId) {
        return FillRedisBo.builder()
                .tradeId(tradeId)
                .status(Constant.FILL_STATUS_CREATE)
                .build();
    }
}