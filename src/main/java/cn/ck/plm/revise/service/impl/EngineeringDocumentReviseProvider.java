/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.revise.service.impl;

import cn.ck.plm.document.service.api.EngineeringDocumentService;
import cn.ck.plm.revise.service.api.ReviseProvider;
import org.springframework.stereotype.Component;

/**
 * 工程数据修订提供者 —— 转调 {@link EngineeringDocumentService#newViewVersion(String)}。
 *
 * <p>覆盖 CAD 设计数据 / 封装 / 图符等共表 ENG_DOCUMENT 的全部软类型。
 */
@Component
public class EngineeringDocumentReviseProvider implements ReviseProvider {

    private final EngineeringDocumentService engineeringDocumentService;

    public EngineeringDocumentReviseProvider(EngineeringDocumentService engineeringDocumentService) {
        this.engineeringDocumentService = engineeringDocumentService;
    }

    @Override
    public String getEntityType() {
        return "ENG_DOCUMENT";
    }

    @Override
    public String getEntityTypeName() {
        return "工程数据";
    }

    @Override
    public void revise(String entityOid, String user) {
        engineeringDocumentService.newViewVersion(entityOid);
    }
}
