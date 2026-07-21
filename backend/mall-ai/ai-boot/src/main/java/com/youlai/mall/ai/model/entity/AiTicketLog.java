package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_ticket_log")
public class AiTicketLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private String action;

    private String detail;

    private String operator;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
