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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预置流程模板市场（M3）—— 把<b>行业包里随包发布的流程</b>按单个流程取用到本租户。
 *
 * <h3>为什么数据源是包目录而不是数据库</h3>
 * <p>包是只读内容（随 jar 发布，也可由实施顾问用外部目录覆盖），租户没取用前不该在
 * {@code ck_process_template} 里有任何痕迹。市场只做"读包 + 投影成清单"，
 * 取用时才走 {@link ProcessTemplateService#create} 落本租户的模板行 ——
 * 于是"装整个包"（{@code ProcessInstaller}）与"只取一个流程"共用同一套落库逻辑。
 *
 * <h3>只列目录形态</h3>
 * <p>只有 {@code processes/*.dsl.json} 才有可编辑的流程定义；只有 {@code .bpmn20.xml} 的流程
 * （如 platform-base 内置的技术审批）没有 DSL，取用后无法在设计器里编辑，
 * 因此<b>不进市场</b>，仍由装包直接部署。
 *
 * <h3>依赖如实标出</h3>
 * <p>流程模板不是自包含的：节点上的 {@code formRef} 指向表单模板，角色 / 类型 / 表单都随包安装。
 * 只取流程而不装包时，那些节点的任务页会渲染不出表单 —— 所以清单里带
 * {@link ProcessTemplateMarketItem#getMissingForms()}，取用前就能看到缺什么。
 */
@Component
public class ProcessTemplateMarket {

    private static final Logger log = LoggerFactory.getLogger(ProcessTemplateMarket.class);

    /** 清单里的资源类型（见 {@code PackManifest.Resource} 的说明） */
    private static final String RESOURCE_PROCESS = "PROCESS";
    /** 目录形态下"可编辑的流程定义"后缀 */
    private static final String DSL_SUFFIX = ".dsl.json";
    /** 取用到本租户时落的分组（按名称匹配，没有就建） */
    private static final String DEFAULT_CATEGORY = "预置流程";
    /** 新分类的排序权重：排在各租户自建分类之后（与 ProcessInstaller 同一口径） */
    private static final int CATEGORY_SORT = 100;

    private final IndustryPackLoader packLoader;
    private final ProcessTemplateService templateService;
    private final ProcessTemplateMapper templateMapper;
    private final ProcessCategoryService categoryService;
    private final ProcessFormTemplateService formService;
    private final ObjectMapper objectMapper;

    public ProcessTemplateMarket(IndustryPackLoader packLoader,
                                 ProcessTemplateService templateService,
                                 ProcessTemplateMapper templateMapper,
                                 ProcessCategoryService categoryService,
                                 ProcessFormTemplateService formService,
                                 ObjectMapper objectMapper) {
        this.packLoader = packLoader;
        this.templateService = templateService;
        this.templateMapper = templateMapper;
        this.categoryService = categoryService;
        this.formService = formService;
        this.objectMapper = objectMapper;
    }

    /**
     * 市场清单：所有包里的 {@code *.dsl.json}，附带"本租户是否已有"与"缺哪些表单"。
     *
     * <p>某个包 / 某个 DSL 读不出来时<b>跳过该条并告警</b>，不让整个清单失败 ——
     * 市场是只读导航，坏一个包不该让用户看不到其余可用的流程。
     */
    public List<ProcessTemplateMarketItem> list() {
        List<ProcessTemplateMarketItem> items = new ArrayList<>();
        Set<String> knownForms = knownFormCodes();

        for (PackSource pack : packLoader.loadAll()) {
            for (PackManifest.Resource resource : pack.getManifest().getResources()) {
                if (!RESOURCE_PROCESS.equalsIgnoreCase(resource.getType()) || resource.getFile() == null) {
                    continue;
                }
                String dir = resource.getFile().trim();
                // 单文件形态（processes.json）里没有逐流程的 DSL，不参与市场
                if (dir.contains(".")) {
                    continue;
                }
                for (String fileName : pack.list(dir)) {
                    if (!fileName.endsWith(DSL_SUFFIX)) {
                        continue;
                    }
                    try {
                        Map<String, Object> dsl = pack.readJsonMap(objectMapper, join(dir, fileName));
                        ProcessTemplateMarketItem item =
                                itemOf(pack, join(dir, fileName), dsl, knownForms);
                        if (item != null) {
                            items.add(item);
                        }
                    } catch (Exception e) {
                        log.warn("读取包内流程 DSL 失败，已跳过: {} / {} — {}", pack.getId(), fileName, e.getMessage());
                    }
                }
            }
        }
        return items;
    }

    /**
     * 取用一个预置流程：把包里的 DSL 复制成本租户的模板（第 1 版）。
     *
     * <p>{@code key} / {@code name} 留空表示沿用 DSL 里的值；给了 key 会<b>同时改写 DSL 的
     * {@code meta.key}</b> —— 模板主档的 key 与 DSL 里的 key 必须是同一个（部署时后端会强校验
     * {@code <process id>} 等于模板 key），只改主档会得到一份"存得住、部署不了"的模板。
     *
     * <p>不部署：市场只负责给出一份可编辑的模板。部署仍由设计器手动触发 ——
     * DSL→BPMN 的编译器在前端，后端没有第二份实现。
     */
    public ProcessTemplate adopt(String packId, String file, String key, String name) {
        PackSource pack = packLoader.find(packId);
        if (pack == null) {
            throw new IllegalArgumentException("包不存在: " + packId);
        }
        Map<String, Object> dsl;
        try {
            dsl = pack.readJsonMap(objectMapper, file);
        } catch (Exception e) {
            throw new IllegalArgumentException("读取包内流程 DSL 失败: " + e.getMessage());
        }
        if (dsl.isEmpty()) {
            throw new IllegalArgumentException("包内没有该流程 DSL: " + file);
        }

        Map<String, Object> meta = asMap(dsl.get("meta"));
        String sourceKey = text(meta.get("key"));
        if (sourceKey == null) {
            throw new IllegalArgumentException("流程 DSL 缺少 meta.key，无法取用: " + file);
        }
        String targetKey = text(key) == null ? sourceKey : text(key);
        if (templateMapper.countByKey(targetKey, null) > 0) {
            throw new IllegalArgumentException("流程 key 已存在: " + targetKey + "，请换一个 key 再取用");
        }

        // 原地改写（dsl 是本次读出的独立 Map，不会影响包文件）
        meta.put("key", targetKey);
        if (text(name) != null) {
            meta.put("name", text(name));
        }
        dsl.put("meta", meta);
        String dslJson;
        try {
            dslJson = objectMapper.writeValueAsString(dsl);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化流程 DSL 失败: " + e.getMessage());
        }

        ProcessTemplate template = new ProcessTemplate();
        template.setKey(targetKey);
        template.setName(text(name) != null ? text(name) : fallbackName(meta, targetKey));
        template.setDisplayName(text(meta.get("displayName")));
        template.setDescription(text(meta.get("description")));
        template.setCategoryOid(ensureCategory());
        template.setEnabled(true);

        ProcessTemplate created = templateService.create(template, dslJson);
        log.info("取用预置流程: pack={} file={} key={} → oid={}", packId, file, targetKey, created.getOid());
        return created;
    }

    // ==================== 内部 ====================

    private ProcessTemplateMarketItem itemOf(PackSource pack, String file, Map<String, Object> dsl,
                                             Set<String> knownForms) {
        Map<String, Object> meta = asMap(dsl.get("meta"));
        String key = text(meta.get("key"));
        if (key == null) {
            log.warn("包内流程 DSL 缺少 meta.key，已跳过: {} / {}", pack.getId(), file);
            return null;
        }
        ProcessTemplateMarketItem item = new ProcessTemplateMarketItem();
        item.setPackId(pack.getId());
        item.setPackName(pack.getManifest().getName());
        item.setPackVersion(pack.getManifest().getVersion());
        item.setFile(file);
        item.setKey(key);
        item.setName(fallbackName(meta, key));
        item.setDisplayName(text(meta.get("displayName")));
        item.setDescription(text(meta.get("description")));
        item.setNodeCount(nodesOf(dsl).size());
        item.setInstalled(templateMapper.countByKey(key, null) > 0);
        item.setMissingForms(missingFormsOf(dsl, knownForms));
        return item;
    }

    /** DSL 里引用了、但本租户表单列表里没有的 formRef（去重、保序） */
    private List<String> missingFormsOf(Map<String, Object> dsl, Set<String> knownForms) {
        Set<String> missing = new LinkedHashSet<>();
        for (Object node : nodesOf(dsl)) {
            if (!(node instanceof Map)) {
                continue;
            }
            String formRef = text(((Map<?, ?>) node).get("formRef"));
            if (formRef != null && !knownForms.contains(formRef)) {
                missing.add(formRef);
            }
        }
        return new ArrayList<>(missing);
    }

    private Set<String> knownFormCodes() {
        Set<String> codes = new LinkedHashSet<>();
        try {
            for (ProcessFormTemplate form : formService.list()) {
                if (form.getCode() != null) {
                    codes.add(form.getCode());
                }
            }
        } catch (Exception e) {
            // 拿不到表单清单时不能把所有模板都标成"缺表单"（那会让市场一片告警）
            log.warn("读取流程表单清单失败，跳过依赖检查: {}", e.getMessage());
        }
        return codes;
    }

    /** 按名称找分组，没有就建一个（分组名租户内唯一，故按名匹配 —— 与 ProcessInstaller 同一口径） */
    private String ensureCategory() {
        for (ProcessCategory category : categoryService.list()) {
            if (DEFAULT_CATEGORY.equals(category.getName())) {
                return category.getOid();
            }
        }
        ProcessCategory created = categoryService.create(DEFAULT_CATEGORY, CATEGORY_SORT, "取用预置流程时自动创建");
        log.info("新建流程分组: {}（{}）", DEFAULT_CATEGORY, created.getOid());
        return created.getOid();
    }

    private static String fallbackName(Map<String, Object> meta, String key) {
        String name = text(meta.get("name"));
        return name != null ? name : key;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new java.util.LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> nodesOf(Map<String, Object> dsl) {
        Object nodes = dsl.get("nodes");
        return nodes instanceof List ? (List<Object>) nodes : List.of();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /** {@code processes} + {@code a.dsl.json} → {@code processes/a.dsl.json}（去掉目录结尾的斜杠） */
    private static String join(String dir, String file) {
        String d = dir == null ? "" : dir.trim();
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d.isEmpty() ? file : d + "/" + file;
    }
}
