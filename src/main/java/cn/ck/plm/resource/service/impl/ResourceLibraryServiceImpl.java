/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.service.api.ClassificationService;
import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceLibraryMapper;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import cn.ck.plm.resource.service.api.ResourceLibraryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 企业资源库服务实现。
 *
 * <p>各资源子库（元器件库 / 标准件库 / 通用件库）管理的对象是 Part 主数据的软类型，
 * 归属「企业资源库 → 对应子库」节点；
 * 分类绑定按 租户 + 资源库节点 维度存储于 {@code ck_library_cls_config}。
 */
@Service
public class ResourceLibraryServiceImpl implements ResourceLibraryService {

    private static final String DEFAULT_RESOURCE_CODE = ResourceContainer.COMPONENT;

    private final ResourceLibraryMapper mapper;
    private final ClassificationService classificationService;
    private final ResourceContainerMapper resourceContainerMapper;

    public ResourceLibraryServiceImpl(ResourceLibraryMapper mapper,
                                       ClassificationService classificationService,
                                       ResourceContainerMapper resourceContainerMapper) {
        this.mapper = mapper;
        this.classificationService = classificationService;
        this.resourceContainerMapper = resourceContainerMapper;
    }

    // ==================== 分类绑定 ====================

    @Override
    public Classification getCategoryTree(String resourceCode) {
        String rootOid = getBindingRootOid(resourceCode);
        if (rootOid == null || rootOid.isEmpty()) {
            return null;
        }
        return classificationService.findSubtree(rootOid);
    }

    @Override
    public String getBindingRootOid(String resourceCode) {
        String code = resolveCode(resourceCode);
        // 1) 当前租户的绑定
        String oid = mapper.selectBindingRootOid(TenantContext.get(), code);
        if (oid != null && !oid.isEmpty()) {
            return oid;
        }
        // 2) 回退平台租户：三个资源子库（COMPONENT/STD_PART/GEN_PART）是平台级共享容器，
        //    其分类绑定可能由平台管理员（sysadmin）在平台租户下维护，
        //    普通租户读取时若本租户无绑定，应回退读取平台级的绑定配置
        if (!TenantContext.PLATFORM_TENANT_OID.equals(TenantContext.get())) {
            return mapper.selectBindingRootOid(TenantContext.PLATFORM_TENANT_OID, code);
        }
        return null;
    }

    @Override
    public Map<String, String> getAllBindings() {
        Map<String, String> map = new HashMap<>();
        // 1) 当前租户
        mergeBindings(mapper.selectAllBindingRootOids(TenantContext.get()), map);
        // 2) 回退平台租户：资源库是平台级共享容器；当前租户有的优先，缺的用平台租户的补
        if (!TenantContext.PLATFORM_TENANT_OID.equals(TenantContext.get())) {
            mergeBindings(mapper.selectAllBindingRootOids(TenantContext.PLATFORM_TENANT_OID), map);
        }
        return map;
    }

    /**
     * 将 mapper 返回的 [{resource_code, root_classification_oid}] 合并到 map。
     * 当前租户优先；平台租户仅补缺（putIfAbsent）。
     */
    private void mergeBindings(List<Map<String, Object>> rows, Map<String, String> map) {
        if (rows == null) return;
        for (Map<String, Object> row : rows) {
            Object rc = row.get("resource_code");
            Object oid = row.get("root_classification_oid");
            if (rc != null && oid != null) {
                // 归一化：去空格 + 转大写，与前端 rows 的 code（COMPONENT/STD_PART/GEN_PART）严格对齐，
                // 避免库中存的 resource_code 大小写/空格不一致导致前端匹配不到
                String rcStr = rc.toString().trim().toUpperCase();
                String oidStr = oid.toString().trim();
                if (!oidStr.isEmpty()) map.putIfAbsent(rcStr, oidStr);
            }
        }
    }

    @Override
    @Transactional
    public void bindCategory(String resourceCode, String rootClassificationOid) {
        String code = resolveCode(resourceCode);
        if (!ResourceContainer.supportsCategoryBinding(code)) {
            throw new IllegalArgumentException("该资源库不支持分类绑定：" + code);
        }
        if (rootClassificationOid == null || rootClassificationOid.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择要绑定的分类节点");
        }
        Classification node = classificationService.findByOid(rootClassificationOid.trim());
        if (node == null) {
            throw new IllegalArgumentException("分类节点不存在: " + rootClassificationOid);
        }
        String tenantOid = TenantContext.get();
        ResourceContainer resourceNode = resourceContainerMapper.selectByCode(code);
        if (mapper.countBinding(tenantOid, code) > 0) {
            mapper.updateBinding(tenantOid, code, rootClassificationOid.trim(), LocalDateTime.now());
        } else {
            mapper.insertBinding(UUID.randomUUID().toString(), tenantOid, code,
                    resourceNode != null ? resourceNode.getOid() : null,
                    rootClassificationOid.trim(), LocalDateTime.now());
        }
    }

    /** 资源库 code 归一：为空时默认元器件库 */
    private String resolveCode(String resourceCode) {
        return (resourceCode == null || resourceCode.trim().isEmpty())
                ? DEFAULT_RESOURCE_CODE
                : resourceCode.trim().toUpperCase();
    }

    // ==================== 资源库容器上下文 ====================

    @Override
    public Map<String, Object> getResourceContext(String resourceCode) {
        String code = resolveCode(resourceCode);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("containerType", ResourceContainer.CONTAINER_TYPE);
        ctx.put("resourceCode", code);
        ctx.put("typeCode", resolveTypeCode(code));
        // 资源子库节点（平台级共享容器，启动时预置）：COMPONENT / STD_PART / GEN_PART
        ResourceContainer lib = resourceContainerMapper.selectByCode(code);
        if (lib != null) {
            ctx.put("containerOid", lib.getOid());
            ctx.put("containerCode", lib.getCode());
            ctx.put("containerName", lib.getName());
            String stageOid = mapper.selectResourceStageOid(lib.getOid());
            if (stageOid == null) {
                // 兜底：阶段缺失时按资源节点补建（平台租户）
                mapper.insertResourceStage(UUID.randomUUID().toString(), code,
                        lib.getName(), lib.getOid(), LocalDateTime.now());
                stageOid = mapper.selectResourceStageOid(lib.getOid());
            }
            ctx.put("stageOid", stageOid);
        } else {
            ctx.put("containerOid", null);
            ctx.put("stageOid", null);
        }
        return ctx;
    }

    // ==================== 元器件清单（ELECTRONIC 软类型的 Part） ====================

    @Override
    public List<Map<String, Object>> findItems(String resourceCode, List<String> clsOids, String keyword) {
        String code = resolveCode(resourceCode);
        String kw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        List<String> oids = (clsOids == null || clsOids.isEmpty()) ? new ArrayList<>() : clsOids;
        return mapper.selectLibraryItems(oids, kw, resolveTypeCode(code));
    }

    /**
     * 资源库 code → 该库管理的对象软类型 code。
     *
     * <p>标准件 / 通用件在 PLM 实践中是「结构件」的一种（铆钉、轴承、垫圈等机械标准件，
     * 以及企业内部跨产品复用的通用结构件），直接使用 PART 下的结构件软类型
     * {@code STRUCTURAL}，挂靠结构设计域（MCAD_DOMAIN）下的对象统一管理。
     * 元器件库则使用电子元器件软类型 {@code ELECTRONIC}。
     */
    private String resolveTypeCode(String resourceCode) {
        switch (resourceCode) {
            case ResourceContainer.STD_PART:
            case ResourceContainer.GEN_PART:
                return "STRUCTURAL";
            default:
                return "ELECTRONIC";
        }
    }
}
