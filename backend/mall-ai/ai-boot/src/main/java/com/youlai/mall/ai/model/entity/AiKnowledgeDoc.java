package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_doc")
public class AiKnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 售后/运营/商品等 */
    private String domain;

    private String fileUrl;

    private String fileName;

    private String lightragDocId;

    /** pending / indexing / ready / failed / local */
    private String status;

    private Long createdBy;

    /** 原文缓存：LightRAG 不可用时本地检索 */
    private String contentText;

    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
