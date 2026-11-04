/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.revise.service.impl;

import cn.ck.plm.part.service.api.PartService;
import cn.ck.plm.revise.service.api.ReviseProvider;
import org.springframework.stereotype.Component;

/**
 * 部件修订提供者 —— 转调 {@link PartService#newViewVersion(String)}。
 *
 * <p>不在此处复制版本逻辑：行操作与流程「object.promote」必须走同一段代码，
 * 否则两处的大版本规则迟早分叉。
 */
@Component
public class PartReviseProvider implements ReviseProvider {

    private final PartService partService;

    public PartReviseProvider(PartService partService) {
        this.partService = partService;
    }

    @Override
    public String getEntityType() {
        return "PART";
    }

    @Override
    public String getEntityTypeName() {
        return "部件";
    }

    @Override
    public void revise(String entityOid, String user) {
        partService.newViewVersion(entityOid);
    }
}
