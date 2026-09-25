/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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
        LifecycleTemplateMaster template = lifecycleTemplateService.findByCode(link.getLifecycleTemplateCode());
        if (template == null || template.getLatestIteration() == null
                || template.getStates() == null || template.getStates().isEmpty()) {
            vo.setReason("生命周期模板「" + link.getLifecycleTemplateCode() + "」没有可用状态定义");
            return vo;
        }

        String templateIterationOid = template.getLatestIteration().getOid();
        Map<String, String> names = nameOf(templateIterationOid, template.getStates());
        Map<String, String> promotes = toMap(template.getTransitions());
        Map<String, String> rejects = toMap(template.getRejections());

        vo.setTemplateCode(template.getCode());
        vo.setTemplateName(template.getName());
        vo.setCurrent(currentStatusCode == null ? null
                : new LifecycleStateOptionsVO.StateInfo(currentStatusCode, names.getOrDefault(currentStatusCode, currentStatusCode)));
        vo.setInitial(new LifecycleStateOptionsVO.StateInfo(initialCode(template), names.get(initialCode(template))));

        for (LifecycleTemplateStatusRef ref : template.getStates()) {
            String code = ref.getStatusCode();
            if (isBlank(code)) {
                continue;
            }
            LifecycleStateOptionsVO.StateOption option = new LifecycleStateOptionsVO.StateOption();
            option.setCode(code);
            option.setName(names.getOrDefault(code, code));
            boolean reachable = reachable(currentStatusCode, code, promotes, rejects);
            option.setReachable(reachable);
            if (!reachable) {
                option.setReason(reasonOf(currentStatusCode, code, promotes, rejects, names));
            }
            vo.getStates().add(option);
        }
        log.info("生命周期状态候选: type={}, template={}, current={}, 状态数={}",
                typeDefinitionCode, template.getCode(), currentStatusCode, vo.getStates().size());
        return vo;
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
            return template.getInitialStateCode().trim();
        }
        return template.getStates().get(0).getStatusCode();
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
