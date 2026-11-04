/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.PackageSymbolMapper;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import cn.ck.plm.resource.service.api.PackageSymbolService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link PackageSymbolService} 实现：封装·图符库（PACKAGE_SYMBOL 资源子库）。
 *
 * <p>资源库**按租户隔离**（2026-10-11 起）：容器与归属阶段都取**本租户**那份，
 * 随 platform-base 包在引导装包时建立；装包前本租户没有该子库 → 上下文返回空
 * （containerOid / stageOid 为 null，前端按空态处理）。
 */
@Service
public class PackageSymbolServiceImpl implements PackageSymbolService {

    private final PackageSymbolMapper mapper;
    private final ResourceContainerMapper resourceContainerMapper;

    public PackageSymbolServiceImpl(PackageSymbolMapper mapper,
                                    ResourceContainerMapper resourceContainerMapper) {
        this.mapper = mapper;
        this.resourceContainerMapper = resourceContainerMapper;
    }

    // ==================== 资源库容器上下文 ====================

    @Override
    public Map<String, Object> getResourceContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("containerType", ResourceContainer.CONTAINER_TYPE);
        String tenantOid = TenantContext.get();
        // 封装·图符归属「企业资源库 → 封装·图符库」子库节点（按租户隔离，装包时各建一份）
        ResourceContainer lib = resourceContainerMapper.selectByCode(ResourceContainer.PACKAGE_SYMBOL, tenantOid);
        if (lib != null) {
            ctx.put("containerOid", lib.getOid());
            ctx.put("containerCode", lib.getCode());
            ctx.put("containerName", lib.getName());
            String stageOid = mapper.selectResourceStageOid(lib.getOid(), tenantOid);
            if (stageOid == null) {
                // 兜底：阶段缺失时按资源节点补建（**本租户**）
                mapper.insertResourceStage(UUID.randomUUID().toString(), ResourceContainer.PACKAGE_SYMBOL,
                        "封装图符", lib.getOid(), tenantOid, LocalDateTime.now());
                stageOid = mapper.selectResourceStageOid(lib.getOid(), tenantOid);
            }
            ctx.put("stageOid", stageOid);
        } else {
            ctx.put("containerOid", null);
            ctx.put("stageOid", null);
        }
        return ctx;
    }

    // ==================== 封装 / 图符清单 ====================

    @Override
    public List<Map<String, Object>> findItems(String folderOid, String typeCode, String keyword) {
        String folder = (folderOid == null || folderOid.trim().isEmpty()) ? null : folderOid.trim();
        String type = (typeCode == null || typeCode.trim().isEmpty()) ? null : typeCode.trim().toUpperCase();
        String kw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        return mapper.selectPackageSymbolItems(folder, type, kw);
    }
}
