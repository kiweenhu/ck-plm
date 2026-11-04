/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.PackageSymbolMapper;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import cn.ck.plm.resource.mapper.ResourceLibraryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 企业资源库安装器（{@code RESOURCE_LIBRARY}）—— 读包内 {@code resourceLibraries.json}。
 *
 * <p>落地三样东西（全部平台级共享）：
 * <ol>
 *   <li>根节点 {@code CORP_RESOURCE}（企业资源库）；</li>
 *   <li>7 个资源子库节点（元器件 / 封装图符 / 标准件 / 通用件 / 技术文档 / 产品图册 / 其他）；</li>
 *   <li>资源子库的<b>归属阶段</b>（{@code ck_stage}，{@code owner_type='LINE'}）——
 *       {@code Part.stage_oid} 非空，资源子库没有 stage 行就没法放部件。</li>
 * </ol>
 *
 * <h3>为什么按容器选不同 Mapper</h3>
 * 平台侧历史上是"各库各自懒建"：{@code ResourceLibraryMapper} 负责元器件/标准件/通用件
 * （描述文案「企业资源库-资源子库归属阶段」），{@code PackageSymbolMapper} 负责封装图符库
 * （描述文案「企业资源库-封装图符库归属阶段」）。这里沿用它们现有的插入语句，
 * 是为了让抽取前后<b>落库结果逐字一致</b>（描述文案属于可见数据，不能悄悄改）。
 * 抽取后行为变为"启动即齐"，同时保留了原有的懒建兜底（存在则跳过）。
 *
 * <p><b>幂等键</b>：节点 = {@code code}；阶段 = 容器 oid + 平台租户（{@code selectResourceStageOid}）。
 */
@Component
public class ResourceLibraryInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(ResourceLibraryInstaller.class);

    private final ResourceContainerMapper containerMapper;
    private final ResourceLibraryMapper resourceLibraryMapper;
    private final PackageSymbolMapper packageSymbolMapper;

    public ResourceLibraryInstaller(ResourceContainerMapper containerMapper,
                                    ResourceLibraryMapper resourceLibraryMapper,
                                    PackageSymbolMapper packageSymbolMapper) {
        this.containerMapper = containerMapper;
        this.resourceLibraryMapper = resourceLibraryMapper;
        this.packageSymbolMapper = packageSymbolMapper;
    }

    @Override
    public String resourceType() {
        return "RESOURCE_LIBRARY";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }

        // 资源库按租户隔离（2026-10-11）：目标租户来自安装上下文
        //（引擎调用安装器期间 TenantContext 也已切到它，这里显式传参保持与 Mapper 一致的口径）
        String tenantOid = context.getTenantOid();

        // 1) 根节点
        ResourceContainer root = containerMapper.selectRoot(tenantOid);
        Object rootDef = json.get("root");
        if (root == null && rootDef instanceof Map) {
            Map<?, ?> def = (Map<?, ?>) rootDef;
            if (context.isDryRun()) {
                results.add(InstallResult.inserted(resourceType(), text(def.get("code"))
                        .toString()).why("预演：将新增资源库根节点「" + text(def.get("name")) + "」"));
                return results;   // 预演：根都没有，子库与阶段跟着没意义
            }
            root = newNode(text(def.get("code")), text(def.get("name")),
                    text(def.get("description")), null, 0, tenantOid);
            containerMapper.insert(root);
            results.add(InstallResult.inserted(resourceType(), root.getCode())
                    .why("资源库根节点：" + root.getName()).on("ck_resource_container", root.getOid()));
        } else if (root != null) {
            results.add(InstallResult.skipped(resourceType(), root.getCode(), "根节点已存在"));
        }
        if (root == null) {
            // 本包没声明根、库里也没有根 —— 如 electronics-core 只声明电子行业的两个子库，
            // 根（CORP_RESOURCE）由 platform-base 建（引擎按「基 → 变体」先装基包，正常路径下根已就位）。
            // 预演：按包内容报"将新增"；真装：必须【明确报失败】—— 否则"一条都没写"会被报成
            // "将新增 N 条"，管理员以为装好了（假就绪比没装更糟，界面还不会提示）。
            if (context.isDryRun()) {
                return previewOnly(context, json, results);
            }
            results.add(InstallResult.failed(resourceType(),
                    resource.getFile() == null ? "resourceLibraries" : resource.getFile(),
                    "资源库根节点不存在：本包只声明资源子库，需先安装声明了根节点的包（platform-base）"));
            log.warn("资源库根节点不存在，{} 的子库未落库（请先安装 platform-base）", context.getSource().getId());
            return results;
        }

        // 2) 资源子库
        Object children = json.get("children");
        if (children instanceof List) {
            int sort = 1;
            for (Object element : (List<?>) children) {
                if (!(element instanceof Map)) {
                    sort++;
                    continue;
                }
                Map<?, ?> def = (Map<?, ?>) element;
                String code = text(def.get("code"));
                String name = text(def.get("name"));
                if (code == null) {
                    sort++;
                    continue;
                }
                if (containerMapper.selectByCode(code, tenantOid) != null) {
                    results.add(InstallResult.skipped(resourceType(), code, "本租户已有该资源子库"));
                } else if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增资源子库「" + name + "」"));
                } else {
                    ResourceContainer node = newNode(code, name,
                            name + "（企业资源库子库）", root.getOid(), sort, tenantOid);
                    containerMapper.insert(node);
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("资源子库：" + name).on("ck_resource_container", node.getOid()));
                }
                sort++;
            }
        }

        // 3) 资源子库归属阶段
        Object stages = json.get("stages");
        if (stages instanceof List) {
            for (Object element : (List<?>) stages) {
                if (!(element instanceof Map)) {
                    continue;
                }
                Map<?, ?> def = (Map<?, ?>) element;
                String containerCode = text(def.get("containerCode"));
                String stageCode = text(def.get("code"));
                String stageName = text(def.get("name"));
                if (containerCode == null || stageCode == null) {
                    continue;
                }
                ResourceContainer container = containerMapper.selectByCode(containerCode, tenantOid);
                if (container == null) {
                    results.add(InstallResult.failed(resourceType(), containerCode + "/" + stageCode,
                            "资源子库不存在，无法挂归属阶段"));
                    continue;
                }
                if (resourceLibraryMapper.selectResourceStageOid(container.getOid(), tenantOid) != null) {
                    results.add(InstallResult.skipped(resourceType(), containerCode + "/" + stageCode,
                            "本租户已有该归属阶段"));
                    continue;
                }
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), containerCode + "/" + stageCode)
                            .why("预演：将为「" + container.getName() + "」新增归属阶段「" + stageName + "」"));
                    continue;
                }
                String oid = UUID.randomUUID().toString();
                LocalDateTime now = LocalDateTime.now();
                // 描述文案由各 Mapper 的 SQL 决定（与历史数据逐字一致），故按容器分派
                if (ResourceContainer.PACKAGE_SYMBOL.equals(containerCode)) {
                    packageSymbolMapper.insertResourceStage(oid, stageCode, stageName,
                            container.getOid(), tenantOid, now);
                } else {
                    resourceLibraryMapper.insertResourceStage(oid, stageCode, stageName,
                            container.getOid(), tenantOid, now);
                }
                results.add(InstallResult.inserted(resourceType(), containerCode + "/" + stageCode)
                        .why("归属阶段：" + stageName + "（" + container.getName() + "）")
                        .on("ck_stage", oid));
            }
        }

        log.debug("企业资源库安装完成: {} 条明细", results.size());
        return results;
    }

    /** 预演且根节点尚未创建时：把包内将创建的内容逐条报出来（不写库） */
    private List<InstallResult> previewOnly(InstallContext context, Map<String, Object> json,
                                            List<InstallResult> results) {
        Object children = json.get("children");
        if (children instanceof List) {
            for (Object element : (List<?>) children) {
                if (element instanceof Map) {
                    Map<?, ?> def = (Map<?, ?>) element;
                    results.add(InstallResult.inserted(resourceType(), String.valueOf(def.get("code")))
                            .why("预演：将新增资源子库「" + def.get("name") + "」"));
                }
            }
        }
        Object stages = json.get("stages");
        if (stages instanceof List) {
            for (Object element : (List<?>) stages) {
                if (element instanceof Map) {
                    Map<?, ?> def = (Map<?, ?>) element;
                    results.add(InstallResult.inserted(resourceType(),
                            def.get("containerCode") + "/" + def.get("code"))
                            .why("预演：将新增归属阶段「" + def.get("name") + "」"));
                }
            }
        }
        return results;
    }

    private ResourceContainer newNode(String code, String name, String description,
                                      String parentOid, int sortOrder, String tenantOid) {
        ResourceContainer node = new ResourceContainer();
        node.setOid(UUID.randomUUID().toString());
        node.setCode(code);
        node.setName(name);
        node.setDescription(description);
        node.setContainerType(ResourceContainer.CONTAINER_TYPE);
        node.setParentOid(parentOid);
        node.setSortOrder(sortOrder);
        node.setTenantOid(tenantOid);
        node.setDeleteMark(false);
        LocalDateTime now = LocalDateTime.now();
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        return node;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
