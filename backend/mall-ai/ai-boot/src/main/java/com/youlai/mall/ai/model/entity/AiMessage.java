package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user / assistant / system / tool */
    private String role;

    private String content;

    private String toolCallsJson;

    private String refsJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
