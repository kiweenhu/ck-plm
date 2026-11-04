/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.entity.LifecycleTemplateTransitionRef;
import cn.ck.plm.base.mapper.LifecycleStatusMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生命周期状态字典安装器（{@code LIFECYCLE}）—— 读包内 {@code lifecycles.json} 的 {@code statuses[]}。
 *
 * <p><b>数据来源变迁</b>：原先这 6 个状态写在 {@code LifecycleStatusInitializer} 里（代码即数据），
 * 现在随 {@code platform-base} 包播种 —— 状态字典是行业无关的基础设施，但把它放包里之后，
 * 别的行业包也能追加自己的状态（如汽车的 PPAP 状态、军工的定型状态）而不用改代码。
 *
 * <p><b>为什么逐条判重</b>：状态被对象引用（{@code ck_*_iteration.status} 存的是 code），
 * 重复插入会撞唯一键；按 code 判重后重复安装天然幂等，也保留管理员改过的显示名。
 */
@Component
public class LifecycleStatusInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(LifecycleStatusInstaller.class);

    private final LifecycleStatusMapper mapper;
    /** 生命周期模板（STANDARD / SIMPLE）由本安装器一并落地，见 {@code lifecycleTemplates[]} */
    private final LifecycleTemplateService lifecycleTemplateService;

    public LifecycleStatusInstaller(LifecycleStatusMapper mapper,
                                    LifecycleTemplateService lifecycleTemplateService) {
        this.mapper = mapper;
        this.lifecycleTemplateService = lifecycleTemplateService;
    }

    @Override
    public String resourceType() {
        return "LIFECYCLE";
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

        Object statuses = json.get("statuses");
        if (!(statuses instanceof List)) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "模板里没有 statuses 段（本包不含生命周期状态）"));
            return results;
        }

        for (Object element : (List<?>) statuses) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            String name = text(row.get("name"));
            if (code == null) {
                continue;
            }
            if (mapper.existsByCode(code) > 0) {
                results.add(InstallResult.skipped(resourceType(), code, "状态已存在"));
                continue;
            }
            if (context.isDryRun()) {
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("预演：将新增状态「" + name + "」").on("ck_lifecycle_status", null));
                continue;
            }
            LifecycleStatus status = new LifecycleStatus(code, name);
            status.setDisplayName(name);
            // 归属由安装目标决定：平台级包 → 平台租户（全租户共享）；行业包 → 该租户
            status.setTenantOid(context.getTenantOid());
            LocalDateTime now = LocalDateTime.now();
            status.setCreatedAt(now);
            status.setUpdatedAt(now);
            mapper.insert(status);
            results.add(InstallResult.inserted(resourceType(), code)
                    .why("状态字典：" + name).on("ck_lifecycle_status", status.getOid()));
        }
        installTemplates(context, json, results);

        log.debug("生命周期状态安装完成: {} 条明细", results.size());
        return results;
    }

    /**
     * 安装生命周期<b>模板</b>（{@code lifecycleTemplates[]}）—— 原先写在
     * {@code TypeDefinitionInitializer#ensureDefaultLifecycleTemplates} 里（STANDARD / SIMPLE），
     * 现随本包播种。模板 = 状态序列 + 升版流转 + 驳回流转，由 Service 一步落库
     * （主表 ck_lifecycle_template + 迭代 + 状态/流转子表）。
     *
     * <p>幂等键：code（{@code exists} 读的是当前 TenantContext 的租户，引擎已切好）。
     */
    private void installTemplates(InstallContext context, Map<String, Object> json,
                                  List<InstallResult> results) {
        Object templates = json.get("lifecycleTemplates");
        if (!(templates instanceof List)) {
            return;
        }
        for (Object element : (List<?>) templates) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            try {
                if (lifecycleTemplateService.exists(code)) {
                    results.add(InstallResult.skipped(resourceType(), code, "生命周期模板已存在"));
                    continue;
                }
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增生命周期模板「" + text(row.get("name")) + "」"));
                    continue;
                }
                LifecycleTemplateMaster template = new LifecycleTemplateMaster();
                template.setOid(UUID.randomUUID().toString());
                template.setCode(code);
                template.setName(text(row.get("name")));
                template.setDescription(text(row.get("description")));
                template.setActive(true);
                template.setInitialStateCode(text(row.get("initialStateCode")));
                template.setTenantOid(context.getTenantOid());

                int stateSort = 1;
                Object states = row.get("states");
                if (states instanceof List) {
                    for (Object stateElement : (List<?>) states) {
                        if (!(stateElement instanceof Map)) {
                            continue;
                        }
                        Map<?, ?> state = (Map<?, ?>) stateElement;
                        int sort = state.get("sortOrder") instanceof Number
                                ? ((Number) state.get("sortOrder")).intValue() : stateSort;
                        LifecycleTemplateStatusRef statusRef = new LifecycleTemplateStatusRef(
                                text(state.get("code")), text(state.get("name")), sort);
                        // revisable 不写默认 true：变更单据（ECR / ECN / ECT）本来就没有修订概念，
                        // 包必须能把「这一状态不可修订」声明下来，否则装出来的模板全是可修订。
                        Object revisable = state.get("revisable");
                        if (revisable instanceof Boolean) {
                            statusRef.setRevisable((Boolean) revisable);
                        }
                        template.getStates().add(statusRef);
                        stateSort++;
                    }
                }
                Object transitions = row.get("transitions");
                if (transitions instanceof List) {
                    for (Object item : (List<?>) transitions) {
                        if (item instanceof Map) {
                            Map<?, ?> tr = (Map<?, ?>) item;
                            template.getTransitions().add(new LifecycleTemplateTransitionRef(
                                    text(tr.get("from")), text(tr.get("to")), "PROMOTE"));
                        }
                    }
                }
                Object rejections = row.get("rejections");
                if (rejections instanceof List) {
                    for (Object item : (List<?>) rejections) {
                        if (item instanceof Map) {
                            Map<?, ?> rj = (Map<?, ?>) item;
                            template.getRejections().add(new LifecycleTemplateTransitionRef(
                                    text(rj.get("from")), text(rj.get("to")), "REJECT"));
                        }
                    }
                }
                lifecycleTemplateService.create(template);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("生命周期模板：" + template.getName() + "（" + template.getStates().size()
                                + " 状态 / " + (template.getTransitions().size()
                                + template.getRejections().size()) + " 条流转）")
                        .on("ck_lifecycle_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
