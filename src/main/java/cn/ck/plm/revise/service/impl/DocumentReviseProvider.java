/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.revise.service.impl;

import cn.ck.plm.document.service.api.DocumentService;
import cn.ck.plm.revise.service.api.ReviseProvider;
import org.springframework.stereotype.Component;

/**
 * 文档修订提供者 —— 转调 {@link DocumentService#newViewVersion(String)}。
 */
@Component
public class DocumentReviseProvider implements ReviseProvider {

    private final DocumentService documentService;

    public DocumentReviseProvider(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String getEntityType() {
        return "DOCUMENT";
    }

    @Override
    public String getEntityTypeName() {
        return "文档";
    }

    @Override
    public void revise(String entityOid, String user) {
        documentService.newViewVersion(entityOid);
    }
}
