package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分块 + 向量（JSON 存 float[]，纯 Java RAG）。
 */
@Data
@TableName("ai_knowledge_chunk")
public class AiKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Integer chunkIndex;

    private String content;

    /** JSON 数组，如 [0.1,0.2,...] */
    private String embeddingJson;

    private Integer embeddingDim;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
