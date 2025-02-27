package com.reconnect.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private String uuid;
    private String id;
    private String link;
    private BigDecimal price;
    private String skuId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 