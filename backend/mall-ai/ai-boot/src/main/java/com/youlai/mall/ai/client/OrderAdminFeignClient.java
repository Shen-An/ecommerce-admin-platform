package com.youlai.mall.ai.client;

import com.youlai.common.result.PageResult;
import com.youlai.mall.ai.client.dto.OrderPageItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理端订单只读查询（返回 PageResult，不使用 FeignDecoder 解包 Result）。
 */
@FeignClient(value = "mall-oms", contextId = "aiOrderAdmin")
public interface OrderAdminFeignClient {

    @GetMapping("/api/v1/orders")
    PageResult<OrderPageItemDTO> getOrderPage(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize,
            @RequestParam(value = "keywords", required = false) String keywords,
            @RequestParam(value = "status", required = false) Integer status
    );
}
