package com.youlai.mall.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * PMS 管理端商品分页行（仅保留助手展示字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpuPageItemDTO {

    private Long id;
    private String name;
    private Long price;
    private Long originPrice;
    private Integer sales;
    private String picUrl;
    private Integer status;
    private String categoryName;
    private String brandName;
    private List<SkuItem> skuList;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkuItem {
        private Long id;
        private String name;
        private String skuSn;
        private Long price;
        private Integer stock;
    }
}
