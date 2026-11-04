/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.entity.LifecycleTemplateTransitionRef;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.softtype.dto.LifecycleStateOptionsVO;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateService;
import cn.ck.plm.softtype.service.api.TypeLifecycleTemplateLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link TypeLifecycleStateService} 的实现：类型 → 绑定模板 → 状态候选项。
 */
@Service
public class DefaultTypeLifecycleStateService implements TypeLifecycleStateService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTypeLifecycleStateService.class);



    private final TypeDefinitionService typeDefinitionService;
    private final TypeLifecycleTemplateLinkService templateLinkService;
    private final LifecycleTemplateService lifecycleTemplateService;
    /** 状态的显示名（模板里的 display_name 优先，见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;

    public DefaultTypeLifecycleStateService(TypeDefinitionService typeDefinitionService,
                                            TypeLifecycleTemplateLinkService templateLinkService,
                                            LifecycleTemplateService lifecycleTemplateService,
                                            LifecycleStatusService lifecycleStatusService) {
        this.typeDefinitionService = typeDefinitionService;
        this.templateLinkService = templateLinkService;
        this.lifecycleTemplateService = lifecycleTemplateService;
        this.lifecycleStatusService = lifecycleStatusService;
    }

    @Override
    public LifecycleStateOptionsVO options(String typeDefinitionCode, String currentStatusCode) {
        return options(typeDefinitionCode, currentStatusCode, null);
    }

    @Override
    public LifecycleStateOptionsVO options(String typeDefinitionCode, String currentStatusCode,
                                           String boundTemplateIterationOid) {
        LifecycleStateOptionsVO vo = new LifecycleStateOptionsVO();
        if (isBlank(typeDefinitionCode)) {
            vo.setReason("缺少类型信息（typeDefinitionCode），无法确定该对象的生命周期模板");
            return vo;
        }
        TypeDefinition type = typeDefinitionService.findByCode(typeDefinitionCode.trim());
        if (type == null) {
            vo.setReason("类型定义不存在: " + typeDefinitionCode);
            return vo;
        }
        TypeLifecycleTemplateLink link = templateLinkService.getByTypeOid(type.getOid());
        if (link == null || isBlank(link.getLifecycleTemplateCode())) {
            vo.setReason("类型「" + typeDefinitionCode + "」还没有绑定生命周期模板，请先在「业务配置」里绑定后再设置状态");
            return vo;
        }
        LifecycleTemplateMaster configured = lifecycleTemplateService.findByCode(link.getLifecycleTemplateCode());
        if (configured == null || configured.getLatestIteration() == null
                || configured.getStates() == null || configured.getStates().isEmpty()) {
            vo.setReason("生命周期模板「" + link.getLifecycleTemplateCode() + "」没有可用状态定义");
            return vo;
        }

        // 对象「出生」时固化的模板版本（迭代里的 lifecycleTemplateIterationOid 反查）。
        // 查不到（历史数据没写、模板被删）就当作没绑定。
        LifecycleTemplateMaster bound = lifecycleTemplateService.findByIterationOid(boundTemplateIterationOid);
        boolean boundUsable = bound != null && bound.getStates() != null && !bound.getStates().isEmpty();
        // 设置状态一律按业务配置的模板：历史对象绑着旧模板 / 旧子版本时，本次操作会先切到新模板，
        // 候选必须与新模板同源（否则界面给旧模板的状态、执行侧按新模板拒收）。
        LifecycleTemplateMaster active = configured;

        vo.setConfiguredTemplateCode(configured.getCode());
        vo.setConfiguredTemplateName(configured.getName());
        if (boundUsable) {
            vo.setBoundTemplateCode(bound.getCode());
            vo.setBoundTemplateName(bound.getName());
        }
        String configuredIterationOid = configured.getLatestIteration().getOid();
        // 绑定的模板 / 子版本与业务配置不是同一版 → 本次会先切换（含同 code 的旧子版本）
        boolean changed = boundUsable
                && !configuredIterationOid.equals(bound.getLatestIteration().getOid());
        boolean bindRequired = !boundUsable;
        vo.setTemplateChanged(changed);
        vo.setTemplateBindRequired(bindRequired);

        // 切换预览：状态按 code 保留，保留不了就落到新模板的初始状态（与 rebindLifecycle 同规则）
        String reachabilityBase = currentStatusCode;
        if (changed || bindRequired) {
            String mapped = mapStatus(configured, currentStatusCode);
            vo.setMigrationStatusCode(mapped);
            vo.setMigrationKeepsStatus(currentStatusCode != null && currentStatusCode.equals(mapped));
            vo.setMigrationStatusName(nameOf(configuredIterationOid, configured.getStates())
                    .getOrDefault(mapped, mapped));
            // 切换可能把当前状态重置到初始状态，候选的可达性要按切换后的状态算
            reachabilityBase = mapped;
        }

        String activeIterationOid = active.getLatestIteration().getOid();
        Map<String, String> names = nameOf(activeIterationOid, active.getStates());
        Map<String, String> promotes = toMap(active.getTransitions());
        Map<String, String> rejects = toMap(active.getRejections());

        vo.setTemplateCode(active.getCode());
        vo.setTemplateName(active.getName());
        vo.setCurrent(currentStatusCode == null ? null
                : new LifecycleStateOptionsVO.StateInfo(currentStatusCode, name(names, currentStatusCode)));
        String initial = initialCode(active);
        vo.setInitial(new LifecycleStateOptionsVO.StateInfo(initial, names.getOrDefault(initial, initial)));

        for (LifecycleTemplateStatusRef ref : active.getStates()) {
            String code = ref.getStatusCode();
            if (isBlank(code)) {
                continue;
            }
            LifecycleStateOptionsVO.StateOption option = new LifecycleStateOptionsVO.StateOption();
            option.setCode(code);
            option.setName(names.getOrDefault(code, code));
            // 没有当前状态时执行侧只允许落到初始状态（见 moveToState），候选必须与之一致，
            // 否则界面会把唯一可选项也标成"不可设置"
            boolean reachable = isBlank(reachabilityBase)
                    ? code.equals(initial)
                    : reachable(reachabilityBase, code, promotes, rejects);
            option.setReachable(reachable);
            if (!reachable) {
                option.setReason(reasonOf(reachabilityBase, code, promotes, rejects, names));
            }
            vo.getStates().add(option);
        }
        log.info("生命周期状态候选: type={}, template={}, current={}, changed={}, bindRequired={}, 状态数={}",
                typeDefinitionCode, active.getCode(), currentStatusCode, changed, bindRequired,
                vo.getStates().size());
        return vo;
    }

    @Override
    public String configuredTemplateIteration(String typeDefinitionCode) {
        if (isBlank(typeDefinitionCode)) {
            return null;
        }
        TypeDefinition type = typeDefinitionService.findByCode(typeDefinitionCode.trim());
        if (type == null) {
            return null;
        }
        TypeLifecycleTemplateLink link = templateLinkService.getByTypeOid(type.getOid());
        if (link == null || isBlank(link.getLifecycleTemplateCode())) {
            return null;
        }
        LifecycleTemplateMaster template = lifecycleTemplateService.findByCode(link.getLifecycleTemplateCode().trim());
        return template != null && template.getLatestIteration() != null
                ? template.getLatestIteration().getOid()
                : null;
    }

    /**
     * 能不能一步迁到目标状态。
     *
     * <p>规则与后端执行时（{@code LifecycleTemplateService#moveToState}）保持一致：
     * 同一状态（重复设置）算可达；否则必须是模板里的升版或回退目标。
     * 对象还没有状态时只允许落到初始状态 —— 与执行侧的判定逐条对应，界面才敢禁用选项。
     */
    private boolean reachable(String current, String target, Map<String, String> promotes,
                              Map<String, String> rejects) {
        if (isBlank(current)) {
            return false;
        }
        if (target.equals(current)) {
            return true;
        }
        return target.equals(promotes.get(current)) || target.equals(rejects.get(current));
    }

    private String reasonOf(String current, String target, Map<String, String> promotes,
                            Map<String, String> rejects, Map<String, String> names) {
        if (isBlank(current)) {
            return "对象还没有生命周期状态，只能设到初始状态";
        }
        String promote = promotes.get(current);
        String reject = rejects.get(current);
        return "当前状态「" + name(names, current) + "」不能直接迁到「" + name(names, target) + "」"
                + "（模板允许：升版 → " + name(names, promote) + "，回退 → " + name(names, reject) + "）";
    }

    /** 模板的初始状态：优先模板上显式配置的，否则取状态清单第一个 */
    private String initialCode(LifecycleTemplateMaster template) {
        if (!isBlank(template.getInitialStateCode())) {
            String configured = template.getInitialStateCode().trim();
            boolean exists = template.getStates().stream()
                    .anyMatch(ref -> configured.equals(ref.getStatusCode()));
            if (exists) {
                return configured;
            }
        }
        return template.getStates().get(0).getStatusCode();
    }

    /** 迁移到该模板后当前状态会变成什么：同 code 保留，否则落到初始状态 */
    private String mapStatus(LifecycleTemplateMaster template, String current) {
        if (!isBlank(current) && template.getStates().stream()
                .anyMatch(ref -> current.equals(ref.getStatusCode()))) {
            return current;
        }
        return initialCode(template);
    }

    /** code → 显示名（模板里的 display_name 优先） */
    private Map<String, String> nameOf(String templateIterationOid, List<LifecycleTemplateStatusRef> states) {
        Map<String, String> names = new LinkedHashMap<>();
        for (LifecycleTemplateStatusRef ref : states) {
            if (isBlank(ref.getStatusCode())) {
                continue;
            }
            String code = ref.getStatusCode().trim();
            String resolved = lifecycleStatusService.displayName(templateIterationOid, code);
            names.put(code, isBlank(resolved) ? code : resolved);
        }
        return names;
    }

    private Map<String, String> toMap(List<LifecycleTemplateTransitionRef> refs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (LifecycleTemplateTransitionRef ref : refs != null ? refs : List.<LifecycleTemplateTransitionRef>of()) {
            if (!isBlank(ref.getFromStatusCode()) && !isBlank(ref.getToStatusCode())) {
                map.put(ref.getFromStatusCode().trim(), ref.getToStatusCode().trim());
            }
        }
        return map;
    }

    private String name(Map<String, String> names, String code) {
        if (isBlank(code)) {
            return "（无）";
        }
        return names.getOrDefault(code, code);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
