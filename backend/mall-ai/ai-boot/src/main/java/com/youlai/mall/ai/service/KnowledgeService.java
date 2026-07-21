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
     * 将本地 local/failed 文档重新推送到 LightRAG 建索引。
     * @return 成功推送篇数
     */
    int reindexToLightRag();

    /**
     * 刷新 indexing 文档状态（track/list 探测 ready/failed）。
     * @return 状态变更篇数
     */
    int refreshIndexStatus();
}
