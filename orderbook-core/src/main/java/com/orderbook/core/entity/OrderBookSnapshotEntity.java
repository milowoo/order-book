package com.orderbook.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_book_snapshot")
public class OrderBookSnapshotEntity {
    private Long id;
    private String symbol;
    private String exchange;
    private String bids;
    private String asks;
    private Integer bidCount;
    private Integer askCount;
    private Long snapshotTime;
    private Long createdAt;
}
