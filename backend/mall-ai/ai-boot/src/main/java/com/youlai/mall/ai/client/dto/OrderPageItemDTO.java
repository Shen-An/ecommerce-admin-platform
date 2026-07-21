package com.youlai.mall.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * OMS 管理端订单分页行（仅保留助手展示字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPageItemDTO {

    private Long id;
    private String orderSn;
    private Long paymentAmount;
    private Integer status;
    private String statusLabel;
    private Integer totalQuantity;
    private Date createTime;
    private String remark;
    private List<OrderItem> orderItems;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItem {
        private Long skuId;
        private String skuName;
        private String picUrl;
        private Long price;
        private Integer quantity;
    }
}
