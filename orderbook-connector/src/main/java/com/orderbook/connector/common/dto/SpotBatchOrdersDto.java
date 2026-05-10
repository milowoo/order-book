package com.orderbook.connector.common.dto;

import lombok.*;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotBatchOrdersDto extends AbstractOrderDto implements Serializable {

    // http header X-CHANNEL-API-CODE, default null
    private String channelApiCode;

    // 批量订单ID
    private List<SpotOrdersV2Req> orderList;
}