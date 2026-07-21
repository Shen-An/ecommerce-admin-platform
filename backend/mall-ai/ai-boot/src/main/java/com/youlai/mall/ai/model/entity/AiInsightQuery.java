package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_insight_query")
public class AiInsightQuery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String question;

    @TableField("plan_json")
    private String planJson;

    @TableField("result_json")
    private String resultJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
