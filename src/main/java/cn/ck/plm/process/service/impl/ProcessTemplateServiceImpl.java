/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.softtype.service.api.TypeLifecycleStateProcessService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.process.dto.ProcessTemplateDeployResult;
import cn.ck.plm.process.dto.ProcessVersionDeleteResult;
import cn.ck.plm.process.entity.ProcessCategory;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.entity.ProcessTemplateVersion;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.mapper.ProcessTemplateVersionMapper;
import cn.ck.plm.process.service.api.ProcessCategoryService;
import cn.ck.plm.process.service.api.ProcessTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link ProcessTemplateService} 默认实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>模板版本与应用版本分离</b>：模板每次保存生成版本；只有部署才产生
 *       Flowable 流程定义版本。二者不总是相等（改了没部署）。</li>
 *   <li><b>编译在前端</b>：本服务不做 DSL→BPMN 翻译，只做防御性校验
 *       （XML 非空 + process id 与模板 key 一致），保证「模板 key 与引擎流程 key 不会错位」。</li>
 *   <li><b>租户隔离复用现有机制</b>：与 {@code ProcessDeploymentSupport} 一致，
 *       部署时 {@code tenantId} 取当前租户，避免与内置流程的租户维度冲突。</li>
 * </ul>
 */
@Service
public class ProcessTemplateServiceImpl implements ProcessTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ProcessTemplateServiceImpl.class);

    /** 从 BPMN XML 中提取第一个 process 的 id（防御性校验用） */
    private static final Pattern PROCESS_ID = Pattern.compile(
            "<process\\s+[^>]*\\bid\\s*=\\s*\"([^\"]+)\"");

    private final ProcessTemplateMapper templateMapper;
    private final ProcessTemplateVersionMapper versionMapper;
    private final ProcessCategoryService categoryService;
    /** 类型 · 生命周期状态 → 流程模板 关联（整体删除流程模板前要检查引用） */
    private final TypeLifecycleStateProcessService typeLifecycleStateProcessService;
    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    public ProcessTemplateServiceImpl(ProcessTemplateMapper templateMapper,
                                      ProcessTemplateVersionMapper versionMapper,
                                      ProcessCategoryService categoryService,
                                      TypeLifecycleStateProcessService typeLifecycleStateProcessService,
                                      RepositoryService repositoryService,
                                      ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.categoryService = categoryService;
        this.typeLifecycleStateProcessService = typeLifecycleStateProcessService;
        this.repositoryService = repositoryService;
        this.objectMapper = objectMapper;
    }

    // ==================== 查询 ====================

    @Override
    public List<ProcessTemplate> list(String keyword, String categoryOid, Boolean enabled) {
        return templateMapper.selectList(trimToNull(keyword), trimToNull(categoryOid), enabled);
    }

    @Override
    public ProcessTemplate get(String oid) {
        return require(oid);
    }

    @Override
    public List<ProcessTemplateVersion> versions(String oid) {
        require(oid);
        return versionMapper.selectByTemplateOid(oid);
    }

    @Override
    public ProcessTemplateVersion version(String oid, Integer version) {
        require(oid);
        if (version == null) {
            throw new IllegalArgumentException("版本号不能为空");
        }
        ProcessTemplateVersion found = versionMapper.selectByTemplateAndVersion(oid, version);
        if (found == null) {
            throw new IllegalArgumentException("版本不存在: " + oid + " v" + version);
        }
        return found;
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional
    public ProcessTemplate create(ProcessTemplate template, String dslJson) {
        if (template == null) {
            throw new IllegalArgumentException("模板不能为空");
        }
        String key = trimToNull(template.getKey());
        String name = trimToNull(template.getName());
        if (key == null) {
            throw new IllegalArgumentException("流程 key 不能为空");
        }
        if (name == null) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (!key.matches("^[A-Za-z][A-Za-z0-9_-]*$")) {
            throw new IllegalArgumentException("流程 key 只能由字母、数字、下划线、连字符组成，且以字母开头");
        }
        if (templateMapper.countByKey(key, null) > 0) {
            throw new IllegalArgumentException("流程 key 已存在: " + key);
        }
        requireValidJson(dslJson);
        // 分组必填且必须已存在：清单页的导航骨架就是分组，指向空气会让模板"掉出"导航
        String categoryOid = categoryService.requireByOid(template.getCategoryOid()).getOid();

        ProcessTemplate entity = new ProcessTemplate();
        entity.setOid(UUID.randomUUID().toString());
        entity.setKey(key);
        entity.setName(name);
        entity.setDisplayName(trimToNull(template.getDisplayName()));
        entity.setCategoryOid(categoryOid);
        entity.setDescription(trimToNull(template.getDescription()));
        entity.setDslJson(dslJson);
        entity.setLatestVersion(1);
        entity.setEnabled(template.getEnabled() == null || template.getEnabled());
        entity.setTenantOid(TenantContext.get());
        entity.setCreator(UserContext.get());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdater(UserContext.get());
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(entity);

        insertVersion(entity.getOid(), 1, dslJson, null);
        log.info("流程模板已创建: key={} v1 oid={}", key, entity.getOid());
        return entity;
    }

    @Override
    @Transactional
    public ProcessTemplate save(String oid, String dslJson, String changeNote) {
        ProcessTemplate template = require(oid);
        requireValidJson(dslJson);

        int nextVersion = versionMapper.selectMaxVersion(oid) + 1;

        template.setDslJson(dslJson);
        template.setLatestVersion(nextVersion);
        template.setUpdater(UserContext.get());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.update(template);

        insertVersion(oid, nextVersion, dslJson, changeNote);
        log.info("流程模板已保存: key={} v{}", template.getKey(), nextVersion);
        return template;
    }

    @Override
    @Transactional
    public ProcessTemplate copy(String oid, String newKey, String newName) {
        ProcessTemplate source = require(oid);
        String key = trimToNull(newKey);
        if (key == null) {
            key = source.getKey() + "_copy";
        }
        if (templateMapper.countByKey(key, null) > 0) {
            throw new IllegalArgumentException("流程 key 已存在: " + key);
        }
        ProcessTemplate copy = new ProcessTemplate();
        copy.setKey(key);
        copy.setName(trimToNull(newName) != null ? newName.trim()
                : source.getName() + " - 副本");
        copy.setDisplayName(source.getDisplayName());
        copy.setCategoryOid(source.getCategoryOid());
        copy.setDescription(source.getDescription());
        copy.setEnabled(true);
        return create(copy, source.getDslJson());
    }

    /**
     * 移动到指定分组。
     *
     * <p>只改 {@code category_oid} 列：分类是模板的治理属性，不属于流程逻辑 ——
     * 因此换组<b>不产生新版本</b>（DSL 一个字节都没变，不该有版本噪音），
     * 也不改 DSL 里的 {@code meta.category} 快照（见 {@link ProcessCategory} 的说明：
     * 归属一律以 category_oid 为准，DSL 里那个名称只是创建时的可读快照）。
     */
    @Override
    @Transactional
    public ProcessTemplate moveToCategory(String oid, String categoryOid) {
        ProcessTemplate template = require(oid);
        String target = categoryService.requireByOid(categoryOid).getOid();
        if (target.equals(template.getCategoryOid())) {
            return template;
        }
        String from = template.getCategoryOid();
        templateMapper.updateCategory(oid, target);
        template.setCategoryOid(target);
        log.info("流程模板已移动分组: key={} {} → {}", template.getKey(), from, target);
        return template;
    }

    @Override
    @Transactional
    public ProcessTemplate setEnabled(String oid, boolean enabled) {
        ProcessTemplate template = require(oid);
        template.setEnabled(enabled);
        template.setUpdater(UserContext.get());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.update(template);
        log.info("流程模板部署开关: key={} → {}", template.getKey(), enabled ? "允许部署" : "停止部署");
        return template;
    }

    /**
     * 按版本删除 —— 流程删除的唯一方式。
     *
     * <p>三种结果都要照顾到（见 {@link ProcessVersionDeleteResult}）：
     * 删掉普通版本 → 只是历史变短；删掉<b>最新版</b> → 主档回落（流程内容变成剩下的最新版）；
     * 删掉<b>最后一个</b>版本 → 该流程整体消失（流程就是它的版本集合）。
     * 已部署的版本一律拒绝 —— 引擎中的流程定义与历史实例仍按 {@code deployment_id} 引用它。
     */
    @Override
    @Transactional
    public ProcessVersionDeleteResult deleteVersions(String oid, List<Integer> versions) {
        ProcessTemplate template = require(oid);
        List<Integer> targets = versions == null ? List.of() : versions.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的版本");
        }

        // 逐个取行：既校验存在性（给出「v2 不存在」这种精确原因，而不是静默少删），
        // 也顺手筛出已部署的那批 —— 只要有一版已部署，整批拒绝（不做"删一半"的部分成功）
        Map<Integer, ProcessTemplateVersion> rows = new LinkedHashMap<>();
        for (Integer version : targets) {
            ProcessTemplateVersion row = versionMapper.selectByTemplateAndVersion(oid, version);
            if (row == null) {
                throw new IllegalArgumentException("版本不存在: v" + version);
            }
            rows.put(version, row);
        }
        List<Integer> locked = rows.values().stream()
                .filter(row -> Boolean.TRUE.equals(row.getDeployed()))
                .map(ProcessTemplateVersion::getVersion)
                .collect(Collectors.toList());
        if (!locked.isEmpty()) {
            throw new IllegalStateException("已部署的版本不能删除（" + deployLabel(locked)
                    + "）：流程引擎中的流程定义与历史实例仍引用它；如不再使用该流程请改为「停止部署」");
        }

        versionMapper.deleteVersions(oid, targets);
        log.info("流程模板已删除版本: key={} {}", template.getKey(), deployLabel(targets));

        ProcessVersionDeleteResult result = new ProcessVersionDeleteResult();
        result.setTemplateOid(oid);
        result.setDeleted(targets);

        int remainingMax = versionMapper.selectMaxVersion(oid);
        if (remainingMax == 0) {
            // 整体删除前检查跨模块引用：类型 · 生命周期状态可能把它当作"该状态使用的流程模板"
            // （类型模块的 ck_type_lifecycle_state_process_link；软引用无外键，故在此兜底）
            int refs = typeLifecycleStateProcessService.countByProcessTemplate(oid);
            if (refs > 0) {
                throw new IllegalStateException("该流程模板已被 " + refs + " 处「类型 · 生命周期状态」绑定引用，不能删除；"
                        + "请先在「业务配置 → 模型定义 → 规则绑定 → 生命周期模板」中解绑后再删");
            }
            // 版本已删空：流程就是它的版本集合，故主档一并删除（这是删掉一个废弃流程的路径）
            templateMapper.deleteByOid(oid);
            result.setTemplateRemoved(true);
            result.setLatestVersion(null);
            log.info("流程模板已整体删除（版本已全部删完）: key={}", template.getKey());
            return result;
        }

        int currentLatest = template.getLatestVersion() == null ? 0 : template.getLatestVersion();
        if (currentLatest != remainingMax) {
            // 删掉的正是"最新版"：主档镜像的是最新版，故回落到剩下的最新版
            ProcessTemplateVersion latest = versionMapper.selectByTemplateAndVersion(oid, remainingMax);
            template.setLatestVersion(remainingMax);
            template.setDslJson(latest.getDslJson());
            template.setUpdater(UserContext.get());
            template.setUpdatedAt(LocalDateTime.now());
            templateMapper.update(template);
            log.info("流程模板内容已回落: key={} 最新版 → v{}", template.getKey(), remainingMax);
        }
        result.setTemplateRemoved(false);
        result.setLatestVersion(remainingMax);
        return result;
    }

    // ==================== 部署 ====================

    @Override
    @Transactional
    public ProcessTemplateDeployResult deploy(String oid, Integer version, String bpmnXml) {
        ProcessTemplate template = require(oid);
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new IllegalStateException(
                    "该流程已「停止部署」，不能发布新版本；如需发布请在流程清单页改为「允许部署」");
        }
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            throw new IllegalArgumentException("BPMN XML 不能为空");
        }
        // 防御性校验：编译产物的 process id 必须等于模板 key，否则会出现
        // 「模板以为部署了 A，引擎里却是 B」的错位（编译层在前端，后端只能这样兜底）
        String processId = extractProcessId(bpmnXml);
        if (processId == null) {
            throw new IllegalArgumentException("BPMN XML 中未找到 <process id=\"...\">");
        }
        if (!processId.equals(template.getKey())) {
            throw new IllegalArgumentException("BPMN 的 process id (" + processId
                    + ") 与模板 key (" + template.getKey() + ") 不一致");
        }

        int target = version != null ? version : defaultVersion(template);
        ProcessTemplateVersion targetVersion =
                versionMapper.selectByTemplateAndVersion(oid, target);
        if (targetVersion == null) {
            throw new IllegalArgumentException("版本不存在: " + oid + " v" + target);
        }
        if (targetVersion.getBpmnXml() != null && !bpmnXml.equals(targetVersion.getBpmnXml())) {
            log.warn("模板 {} v{} 重复部署且 XML 有变化，将以本次编译产物为准", template.getKey(), target);
        }

        String deploymentId;
        String processDefinitionId = null;
        Integer processDefinitionVersion = null;
        // 流程定义的分组：取模板分组的【名称】。BPMN 里没有分组概念（分组是模板的治理属性，
        // 不属于流程逻辑），不显式设置的话引擎会落到 targetNamespace 上 ——
        // 于是 act_re_procdef.category_ 里是一串 URL，而不是用户看得懂的「物料流程」。
        String categoryName = categoryService.requireByOid(template.getCategoryOid()).getName();
        try {
            Deployment deployment = repositoryService.createDeployment()
                    .name(displayName(template) + " v" + target)
                    .key(template.getKey())
                    .category(categoryName)
                    .tenantId(TenantContext.get())
                    .addString(template.getKey() + ".bpmn20.xml", bpmnXml)
                    .deploy();
            deploymentId = deployment.getId();

            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deploymentId)
                    .singleResult();
            if (definition != null) {
                processDefinitionId = definition.getId();
                processDefinitionVersion = definition.getVersion();
                // 部署上的 category 未必会带到定义上（取决于引擎是否从 BPMN 的 category 属性取值），
                // 故这里对齐一次 —— 定义上的值才是查询/展示真正读的地方
                if (!categoryName.equals(definition.getCategory())) {
                    repositoryService.setProcessDefinitionCategory(definition.getId(), categoryName);
                }
            }
        } catch (Exception e) {
            // 部署失败必须显式抛出：不能把「编译出的 XML 引擎不认」吞成静默成功
            throw new IllegalStateException("流程部署失败: " + e.getMessage(), e);
        }

        versionMapper.markDeployed(oid, target, bpmnXml, deploymentId);
        template.setDeployedVersion(target);
        template.setDeploymentId(deploymentId);
        template.setProcessDefinitionId(processDefinitionId);
        template.setDeployedAt(LocalDateTime.now());
        template.setUpdater(UserContext.get());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.update(template);

        ProcessTemplateDeployResult result = new ProcessTemplateDeployResult();
        result.setTemplateOid(oid);
        result.setKey(template.getKey());
        result.setVersion(target);
        result.setDeploymentId(deploymentId);
        result.setProcessDefinitionId(processDefinitionId);
        result.setProcessDefinitionVersion(processDefinitionVersion);
        log.info("流程模板已部署: key={} 模板v{} → 定义v{} (deploymentId={})",
                template.getKey(), target, processDefinitionVersion, deploymentId);
        return result;
    }

    // ==================== 私有工具 ====================

    private ProcessTemplate require(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            throw new IllegalArgumentException("模板 oid 不能为空");
        }
        ProcessTemplate template = templateMapper.selectByOid(oid.trim());
        if (template == null) {
            throw new IllegalArgumentException("流程模板不存在: " + oid);
        }
        return template;
    }

    private void insertVersion(String templateOid, int version, String dslJson, String changeNote) {
        ProcessTemplateVersion entity = new ProcessTemplateVersion();
        entity.setOid(UUID.randomUUID().toString());
        entity.setTemplateOid(templateOid);
        entity.setVersion(version);
        entity.setDslJson(dslJson);
        entity.setChangeNote(trimToNull(changeNote));
        entity.setDeployed(false);
        entity.setTenantOid(TenantContext.get());
        entity.setCreator(UserContext.get());
        entity.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(entity);
    }

    /** DSL 必须是合法 JSON —— 后端不做 schema 校验（那是前端 dsl-core 的职责），只挡明显坏数据 */
    private void requireValidJson(String dslJson) {
        if (dslJson == null || dslJson.trim().isEmpty()) {
            throw new IllegalArgumentException("DSL 内容不能为空");
        }
        try {
            objectMapper.readTree(dslJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("DSL 不是合法 JSON: " + e.getMessage());
        }
    }

    private String extractProcessId(String bpmnXml) {
        Matcher matcher = PROCESS_ID.matcher(bpmnXml);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int defaultVersion(ProcessTemplate template) {
        Integer latest = template.getLatestVersion();
        return latest == null || latest < 1 ? 1 : latest;
    }

    private String displayName(ProcessTemplate template) {
        return template.getDisplayName() != null && !template.getDisplayName().trim().isEmpty()
                ? template.getDisplayName().trim() : template.getName();
    }

    /**
     * 已部署版本号列表 → 可读标签（如「v1、v3」；超过 5 个只列前 5 个 + 总数）。
     *
     * <p>不把整串版本号塞进提示语：一个长期演进的流程可能部署过几十版，
     * 报错要一眼读懂，细节在「版本历史」里看。
     */
    private static String deployLabel(List<Integer> versions) {
        String head = versions.stream().limit(5).map(v -> "v" + v).collect(Collectors.joining("、"));
        return versions.size() > 5 ? head + " 等 " + versions.size() + " 个版本" : head;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
