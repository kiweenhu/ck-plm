/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.impl;

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
        // 封装·图符归属「企业资源库 → 封装·图符库」子库节点（平台级共享容器，启动时预置）
        ResourceContainer lib = resourceContainerMapper.selectByCode(ResourceContainer.PACKAGE_SYMBOL);
        if (lib != null) {
            ctx.put("containerOid", lib.getOid());
            ctx.put("containerCode", lib.getCode());
            ctx.put("containerName", lib.getName());
            String stageOid = mapper.selectResourceStageOid(lib.getOid());
            if (stageOid == null) {
                // 兜底：阶段缺失时按资源节点补建（平台租户）
                mapper.insertResourceStage(UUID.randomUUID().toString(), ResourceContainer.PACKAGE_SYMBOL,
                        "封装图符", lib.getOid(), LocalDateTime.now());
                stageOid = mapper.selectResourceStageOid(lib.getOid());
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
