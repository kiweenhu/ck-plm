/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import cn.ck.plm.resource.mapper.TechDocMapper;
import cn.ck.plm.resource.service.api.TechDocService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link TechDocService} 实现：技术文档知识库（TECH_DOC 资源子库）。
 *
 * <p>资源库按租户隔离：容器与归属阶段都取本租户那份，随 platform-base 包在引导装包时建立。
 * 装包前本租户没有该子库 → 上下文返回空（containerOid / stageOid 为 null）。
 */
@Service
public class TechDocServiceImpl implements TechDocService {

    private final TechDocMapper mapper;
    private final ResourceContainerMapper resourceContainerMapper;

    public TechDocServiceImpl(TechDocMapper mapper,
                              ResourceContainerMapper resourceContainerMapper) {
        this.mapper = mapper;
        this.resourceContainerMapper = resourceContainerMapper;
    }

    @Override
    public Map<String, Object> getResourceContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("containerType", ResourceContainer.CONTAINER_TYPE);
        String tenantOid = TenantContext.get();
        ResourceContainer lib = resourceContainerMapper.selectByCode(ResourceContainer.TECH_DOC, tenantOid);
        if (lib == null) {
            ctx.put("containerOid", null);
            ctx.put("stageOid", null);
            return ctx;
        }
        ctx.put("containerOid", lib.getOid());
        ctx.put("containerCode", lib.getCode());
        ctx.put("containerName", lib.getName());
        String stageOid = mapper.selectResourceStageOid(lib.getOid(), tenantOid);
        if (stageOid == null) {
            // 兜底：阶段缺失时按资源节点补建（本租户）。技术文档目录树要靠它挂文件夹。
            mapper.insertResourceStage(UUID.randomUUID().toString(), ResourceContainer.TECH_DOC,
                    "技术文档", lib.getOid(), tenantOid, LocalDateTime.now());
            stageOid = mapper.selectResourceStageOid(lib.getOid(), tenantOid);
        }
        ctx.put("stageOid", stageOid);
        return ctx;
    }

    @Override
    public List<Map<String, Object>> findItems(String folderOid, String keyword) {
        Map<String, Object> ctx = getResourceContext();
        Object containerOid = ctx.get("containerOid");
        if (containerOid == null) {
            // 子库没装出来：返回空列表而不是全表扫（containerOid 为 null 会让 WHERE 永远匹配不到）
            return new ArrayList<>();
        }
        String folder = (folderOid == null || folderOid.trim().isEmpty()) ? null : folderOid.trim();
        String kw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        return mapper.selectTechDocItems(containerOid.toString(), folder, kw);
    }
}
