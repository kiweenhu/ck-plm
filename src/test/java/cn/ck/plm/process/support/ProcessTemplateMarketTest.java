/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.process.dto.ProcessTemplateMarketItem;
import cn.ck.plm.process.entity.ProcessCategory;
import cn.ck.plm.process.entity.ProcessFormTemplate;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.service.api.ProcessCategoryService;
import cn.ck.plm.process.service.api.ProcessFormTemplateService;
import cn.ck.plm.process.service.api.ProcessTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预置流程模板市场：清单投影与取用的规则。
 *
 * <p>重点守三件事：<b>缺 key 的 DSL 不进清单</b>（取用不了）、
 * <b>缺表单如实标出</b>（不装包也能看到依赖）、
 * <b>取用时改写 meta.key</b>（主档 key 与 DSL key 必须一致，否则部署必被拒）。
 */
class ProcessTemplateMarketTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IndustryPackLoader packLoader;
    private ProcessTemplateService templateService;
    private ProcessTemplateMapper templateMapper;
    private ProcessCategoryService categoryService;
    private ProcessFormTemplateService formService;
    private ProcessTemplateMarket market;

    @BeforeEach
    void setUp() {
        packLoader = mock(IndustryPackLoader.class);
        templateService = mock(ProcessTemplateService.class);
        templateMapper = mock(ProcessTemplateMapper.class);
        categoryService = mock(ProcessCategoryService.class);
        formService = mock(ProcessFormTemplateService.class);
        market = new ProcessTemplateMarket(
                packLoader, templateService, templateMapper, categoryService, formService, objectMapper);
    }

    @Test
    void listSkipsDslWithoutKeyAndMarksInstalled() throws Exception {
        PackSource pack = packOf("electronics-core@v1.0.0", "电子行业包");
        when(packLoader.loadAll()).thenReturn(List.of(pack));
        when(pack.list("processes")).thenReturn(List.of("ok.dsl.json", "no-key.dsl.json", "notes.md"));
        when(pack.readJsonMap(any(), eq("processes/ok.dsl.json")))
                .thenReturn(dsl("component_intro", "新料导入", "FORM_A"));
        when(pack.readJsonMap(any(), eq("processes/no-key.dsl.json")))
                .thenReturn(Map.of("nodes", List.of()));
        when(formService.list()).thenReturn(List.of(form("FORM_A")));
        when(templateMapper.countByKey("component_intro", null)).thenReturn(1);

        List<ProcessTemplateMarketItem> items = market.list();

        assertEquals(1, items.size(), "缺 meta.key 的 DSL 应当被跳过");
        ProcessTemplateMarketItem item = items.get(0);
        assertEquals("component_intro", item.getKey());
        assertEquals("electronics-core@v1.0.0", item.getPackId());
        assertEquals(1, item.getNodeCount());
        assertTrue(item.isInstalled(), "本租户已有同 key 模板时要标成已取用");
        assertEquals(List.of(), item.getMissingForms());
    }

    @Test
    void listReportsMissingFormsWhenPackNotInstalled() throws Exception {
        PackSource pack = packOf("electronics-core@v1.0.0", "电子行业包");
        when(packLoader.loadAll()).thenReturn(List.of(pack));
        when(pack.list("processes")).thenReturn(List.of("ok.dsl.json"));
        when(pack.readJsonMap(any(), eq("processes/ok.dsl.json")))
                .thenReturn(dsl("component_intro", "新料导入", "FORM_A"));
        // 本租户一个表单都没有 —— 也就是没装过包
        when(formService.list()).thenReturn(List.of());

        ProcessTemplateMarketItem item = market.list().get(0);

        assertEquals(List.of("FORM_A"), item.getMissingForms(), "引用了但不存在的表单要列出来");
        assertFalse(item.isInstalled());
    }

    @Test
    void adoptRewritesMetaKeyToKeepItConsistentWithTemplateRow() throws Exception {
        PackSource pack = packOf("electronics-core@v1.0.0", "电子行业包");
        when(packLoader.find("electronics-core@v1.0.0")).thenReturn(pack);
        when(pack.readJsonMap(any(), eq("processes/ok.dsl.json")))
                .thenReturn(dsl("component_intro", "新料导入", null));
        when(templateMapper.countByKey("tenant_intro", null)).thenReturn(0);
        when(categoryService.list()).thenReturn(List.of(category("cat_pre", "预置流程")));
        when(templateService.create(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessTemplate created =
                market.adopt("electronics-core@v1.0.0", "processes/ok.dsl.json", "tenant_intro", "本厂新料导入");

        assertEquals("tenant_intro", created.getKey());
        assertEquals("本厂新料导入", created.getName());
        assertEquals("cat_pre", created.getCategoryOid(), "要落到「预置流程」分组，没有就建");
        verify(categoryService, never()).create(anyString(), any(), anyString());

        ArgumentCaptor<String> dslJson = ArgumentCaptor.forClass(String.class);
        verify(templateService).create(any(ProcessTemplate.class), dslJson.capture());
        JsonNode json = readTree(dslJson.getValue());
        assertEquals("tenant_intro", json.at("/meta/key").asText(), "DSL 里的 key 必须跟着改");
        assertEquals("本厂新料导入", json.at("/meta/name").asText());
    }

    @Test
    void adoptRejectsDuplicatedKey() throws Exception {
        PackSource pack = packOf("electronics-core@v1.0.0", "电子行业包");
        when(packLoader.find("electronics-core@v1.0.0")).thenReturn(pack);
        when(pack.readJsonMap(any(), eq("processes/ok.dsl.json")))
                .thenReturn(dsl("component_intro", "新料导入", null));
        when(templateMapper.countByKey("component_intro", null)).thenReturn(1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> market.adopt("electronics-core@v1.0.0", "processes/ok.dsl.json", null, null));

        assertTrue(error.getMessage().contains("key 已存在"));
        verify(templateService, never()).create(any(), anyString());
    }

    @Test
    void adoptRejectsUnknownPack() {
        when(packLoader.find("missing@v1")).thenReturn(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> market.adopt("missing@v1", "processes/ok.dsl.json", null, null));

        assertTrue(error.getMessage().contains("包不存在"));
    }

    // ==================== 夹具 ====================

    private PackSource packOf(String id, String name) {
        PackManifest.Resource resource = new PackManifest.Resource();
        resource.setType("PROCESS");
        resource.setFile("processes");

        PackManifest manifest = mock(PackManifest.class);
        when(manifest.getName()).thenReturn(name);
        when(manifest.getVersion()).thenReturn("v1.0.0");
        when(manifest.getResources()).thenReturn(List.of(resource));

        PackSource pack = mock(PackSource.class);
        when(pack.getId()).thenReturn(id);
        when(pack.getManifest()).thenReturn(manifest);
        return pack;
    }

    private static Map<String, Object> dsl(String key, String name, String formRef) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", key);
        meta.put("name", name);
        List<Object> nodes = new ArrayList<>();
        if (formRef != null) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "approval_1");
            node.put("formRef", formRef);
            nodes.add(node);
        }
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("meta", meta);
        dsl.put("nodes", nodes);
        return dsl;
    }

    private static ProcessFormTemplate form(String code) {
        ProcessFormTemplate template = new ProcessFormTemplate();
        template.setCode(code);
        return template;
    }

    private static ProcessCategory category(String oid, String name) {
        ProcessCategory category = new ProcessCategory();
        category.setOid(oid);
        category.setName(name);
        return category;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
