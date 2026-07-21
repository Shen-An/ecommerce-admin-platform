package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_session")
public class AiSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** assistant / knowledge / ticket / insight */
    private String scene;

    private String title;

    /** 1 进行中 0 结束 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
