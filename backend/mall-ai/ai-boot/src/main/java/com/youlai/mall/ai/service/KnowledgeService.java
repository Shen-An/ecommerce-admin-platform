package com.youlai.mall.ai.service;

import com.youlai.mall.ai.model.form.KnowledgeQueryForm;
import com.youlai.mall.ai.model.form.KnowledgeTextForm;
import com.youlai.mall.ai.model.vo.KnowledgeDocVO;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface KnowledgeService {

    Map<String, Object> status();

    List<KnowledgeDocVO> listDocs();

    KnowledgeDocVO ingestText(KnowledgeTextForm form);

    KnowledgeDocVO ingestFile(MultipartFile file, String domain, String title);

    void deleteDoc(Long id);

    KnowledgeQueryVO query(KnowledgeQueryForm form);

    /** 灌入演示语料（幂等） */
    int seedDemoDocs();

    /**
     * 用模型配置页 Embedding 重建全部文档的 Java 向量索引。
     * @return 处理的文档数
     */
    int reindexJavaVectors();

    /**
     * @deprecated 兼容旧前端：等同 {@link #reindexJavaVectors()}
     */
    int reindexToLightRag();

    /**
     * 刷新文档状态（可选 LightRAG track）。
     */
    int refreshIndexStatus();
}
