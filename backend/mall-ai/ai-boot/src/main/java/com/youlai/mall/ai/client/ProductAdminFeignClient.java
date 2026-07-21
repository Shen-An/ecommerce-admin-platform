package com.youlai.mall.ai.client;

import com.youlai.common.result.PageResult;
import com.youlai.mall.ai.client.dto.SpuPageItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理端商品只读查询。
 */
@FeignClient(value = "mall-pms", contextId = "aiProductAdmin")
public interface ProductAdminFeignClient {

    @GetMapping("/api/v1/spu/page")
    PageResult<SpuPageItemDTO> listPagedSpu(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize,
            @RequestParam(value = "keywords", required = false) String keywords
    );
}
