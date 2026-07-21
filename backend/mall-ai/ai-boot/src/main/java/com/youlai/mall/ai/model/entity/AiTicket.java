package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_ticket")
public class AiTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String orderSn;

    /** 投诉/退款/物流/咨询 */
    private String intent;

    /** low / medium / high */
    private String priority;

    /** open / processing / escalated / closed */
    private String status;

    private String summary;

    private String assignee;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
