package com.orderbook.connector.common.dto;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotFillsOrderDTO extends AbstractOrderDto implements Serializable {

    private static final long serialVersionUID = 5466418026957032835L;

    private String orderId;
    private Long after;
    private Long before;

    // 默认100，最大500
    private Integer limit = 500;
}