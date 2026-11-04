/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.entity.LifecycleTemplateIteration;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.entity.LifecycleTemplateTransitionRef;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.softtype.dto.LifecycleStateOptionsVO;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import cn.ck.plm.softtype.service.api.TypeLifecycleTemplateLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「设置生命周期状态」候选项：历史对象绑着旧模板 / 旧子版本 / 未绑定时，
 * 候选一律按业务配置的模板给，并标记"本次会先切换模板"。
 */
class DefaultTypeLifecycleStateServiceTest {

    private TypeDefinitionService typeDefinitionService;
    private TypeLifecycleTemplateLinkService templateLinkService;
    private LifecycleTemplateService lifecycleTemplateService;
    private LifecycleStatusService lifecycleStatusService;
    private DefaultTypeLifecycleStateService service;

    @BeforeEach
    void setUp() {
        typeDefinitionService = mock(TypeDefinitionService.class);
        templateLinkService = mock(TypeLifecycleTemplateLinkService.class);
        lifecycleTemplateService = mock(LifecycleTemplateService.class);
        lifecycleStatusService = mock(LifecycleStatusService.class);
        service = new DefaultTypeLifecycleStateService(typeDefinitionService, templateLinkService,
                lifecycleTemplateService, lifecycleStatusService);

        TypeDefinition type = new TypeDefinition();
        type.setOid("type-1");
        when(typeDefinitionService.findByCode("ELECTRONIC")).thenReturn(type);

        TypeLifecycleTemplateLink link = new TypeLifecycleTemplateLink();
        link.setLifecycleTemplateCode("DOC_REVIEW");
        when(templateLinkService.getByTypeOid("type-1")).thenReturn(link);

        // 业务配置：文档评审生命周期（DRAFT → REVIEWING）
        when(lifecycleTemplateService.findByCode("DOC_REVIEW")).thenReturn(template(
                "DOC_REVIEW", "文档评审生命周期", "ti-review",
                List.of("DRAFT", "REVIEWING"),
                new String[][] { { "DRAFT", "REVIEWING" } }, new String[][] {}));
        // 对象固化：标准生命周期（DRAFT → RELEASED）
        when(lifecycleTemplateService.findByIterationOid("ti-standard")).thenReturn(template(
                "STANDARD", "标准生命周期", "ti-standard",
                List.of("DRAFT", "RELEASED"),
                new String[][] { { "DRAFT", "RELEASED" } }, new String[][] {}));

        when(lifecycleStatusService.displayName(anyString(), anyString()))
                .thenAnswer(inv -> "名-" + inv.getArgument(1));
    }

    @Test
    void 旧模板绑定_候选来自新模板且状态按code保留() {
        LifecycleStateOptionsVO options = service.options("ELECTRONIC", "DRAFT", "ti-standard");

        assertTrue(options.isTemplateChanged(), "对象绑定 STANDARD、业务配置 DOC_REVIEW，必须提示会切换");
        assertEquals("DOC_REVIEW", options.getTemplateCode(), "候选必须来自业务配置的新模板（本次会先切过去）");
        assertEquals("STANDARD", options.getBoundTemplateCode());
        assertEquals("DOC_REVIEW", options.getConfiguredTemplateCode());
        assertEquals("DRAFT", options.getMigrationStatusCode());
        assertTrue(options.getMigrationKeepsStatus(), "DRAFT 在新模板里也存在，切换后状态不变");
        assertTrue(options.getStates().stream()
                .anyMatch(s -> "REVIEWING".equals(s.getCode()) && s.isReachable()));
        assertFalse(options.getStates().stream().anyMatch(s -> "RELEASED".equals(s.getCode())),
                "旧模板的 RELEASED 不能出现在新模板的候选里");
    }

    @Test
    void 旧状态在新模板不存在_按切换后的初始状态算可达性() {
        // 旧模板当前状态 RELEASED 在新模板（DOC_REVIEW）里不存在 → 切换后落到初始状态 DRAFT
        LifecycleStateOptionsVO options = service.options("ELECTRONIC", "RELEASED", "ti-standard");

        assertTrue(options.isTemplateChanged());
        assertEquals("DOC_REVIEW", options.getTemplateCode());
        assertFalse(options.getMigrationKeepsStatus());
        assertEquals("DRAFT", options.getMigrationStatusCode());
        assertEquals("名-DRAFT", options.getMigrationStatusName());
        // 可达性按切换后的 DRAFT 算：DRAFT 自身可选，REVIEWING 也在 DRAFT 的升版规则里
        assertTrue(options.getStates().stream()
                .anyMatch(s -> "DRAFT".equals(s.getCode()) && s.isReachable()));
        assertTrue(options.getStates().stream()
                .anyMatch(s -> "REVIEWING".equals(s.getCode()) && s.isReachable()));
    }

    @Test
    void 同一模板的旧子版本_也按需要切换处理() {
        when(lifecycleTemplateService.findByIterationOid("ti-review-old")).thenReturn(template(
                "DOC_REVIEW", "文档评审生命周期", "ti-review-old",
                List.of("DRAFT", "REVIEWING"),
                new String[][] { { "DRAFT", "REVIEWING" } }, new String[][] {}));

        LifecycleStateOptionsVO options = service.options("ELECTRONIC", "DRAFT", "ti-review-old");

        assertTrue(options.isTemplateChanged(), "code 相同但子版本落后，同样要切到业务配置的最新版");
        assertEquals("DOC_REVIEW", options.getTemplateCode());
    }

    @Test
    void 未绑定模板_标记需要绑定到业务配置的模板() {
        LifecycleStateOptionsVO options = service.options("ELECTRONIC", "DRAFT", null);

        assertFalse(options.isTemplateChanged(), "没有旧模板，不属于「旧→新」切换");
        assertTrue(options.isTemplateBindRequired(), "未绑定要提示本次会绑定新模板");
        assertEquals("DOC_REVIEW", options.getTemplateCode());
    }

    @Test
    void configuredTemplateIterationReturnsTheLatestIterationOfTheConfiguredTemplate() {
        assertEquals("ti-review", service.configuredTemplateIteration("ELECTRONIC"));
    }

    private static LifecycleTemplateMaster template(String code, String name, String iterationOid,
                                                    List<String> states,
                                                    String[][] promotes, String[][] rejects) {
        LifecycleTemplateMaster master = new LifecycleTemplateMaster();
        master.setCode(code);
        master.setName(name);
        master.setInitialStateCode(states.get(0));
        List<LifecycleTemplateStatusRef> statusRefs = new ArrayList<>();
        for (String state : states) {
            LifecycleTemplateStatusRef ref = new LifecycleTemplateStatusRef();
            ref.setStatusCode(state);
            ref.setStatusDisplayName("名-" + state);
            statusRefs.add(ref);
        }
        master.setStates(statusRefs);
        master.setTransitions(transitions(promotes));
        master.setRejections(transitions(rejects));

        LifecycleTemplateIteration iteration = new LifecycleTemplateIteration();
        iteration.setOid(iterationOid);
        master.setLatestIteration(iteration);
        return master;
    }

    private static List<LifecycleTemplateTransitionRef> transitions(String[][] pairs) {
        List<LifecycleTemplateTransitionRef> refs = new ArrayList<>();
        for (String[] pair : pairs) {
            LifecycleTemplateTransitionRef ref = new LifecycleTemplateTransitionRef();
            ref.setFromStatusCode(pair[0]);
            ref.setToStatusCode(pair[1]);
            refs.add(ref);
        }
        return refs;
    }
}
