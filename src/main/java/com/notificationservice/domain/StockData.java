package com.notificationservice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StockData {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("name")
    private String name;

    @JsonProperty("sector")
    private String sector;

    @JsonProperty("current_price")
    private BigDecimal currentPrice;

    @JsonProperty("open_price")
    private BigDecimal openPrice;

    @JsonProperty("high_price")
    private BigDecimal highPrice;

    @JsonProperty("low_price")
    private BigDecimal lowPrice;

    @JsonProperty("volume")
    private long volume;

    @JsonProperty("listed_at")
    private String listedAt;
}
