/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.process.entity.ProcessCategory;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.service.api.ProcessCategoryService;
import cn.ck.plm.process.service.api.ProcessTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 流程安装器（{@code PROCESS}）—— 读包内流程资源，支持两种形态：
 *
 * <h3>① 目录形态（推荐，{@code "file": "processes"}）</h3>
 * 一个流程一对文件，多个流程就是多个文件 —— 不必挤在一个大 JSON 里，改动 diff 看得清：
 * <pre>
 * processes/
 *   component-introduction.dsl.json     设计器可编辑的流程定义（模板 + 第 1 版）
 *   component-introduction.bpmn20.xml   由同一份 DSL 编出的 BPMN（可直部署，装完即可发起）
 * </pre>
 * 同名即一对（按 basename 配对）；只给一半也认：
 * <ul>
 *   <li><b>只有 .dsl.json</b> → 建模板与第 1 版；<i>不部署</i>（BPMN 由设计器编译，需人工点「部署」）；</li>
 *   <li><b>只有 .bpmn20.xml</b> → 直接部署到 Flowable；<i>没有模板行</i>，故设计器里看不到、不能可视化编辑
 *       （内置流程 {@code plm-change-review} 就是这种形态）；报告里会写清；</li>
 *   <li><b>成对</b> → 建模板 + 用它自己的 BPMN 部署（走平台的部署服务，版本号/部署标识一并记好）。</li>
 * </ul>
 *
 * <h3>② 单文件形态（兼容旧包，{@code "file": "processes.json"}）</h3>
 * 一个 JSON 里 {@code processes[]} 数组，每项带 {@code dsl}。等价于"只有 DSL"，不部署。
 *
 * <h3>为什么后端不自己编 BPMN</h3>
 * DSL→BPMN 的编译器在前端（{@code flow-designer/bpmn-compiler}）✓，后端没有第二份实现 ——
 * 所以包里的 {@code .bpmn20.xml} 是<b>用前端编译器预先编好的</b>（与 DSL 同源），
 * 后端只负责"部署现成的 XML"。这样不会出现"两套编译器各说各话"。
 *
 * <h3>幂等与租户口径</h3>
 * 模板按 key 判重（租户内唯一），已存在即跳过、<b>不覆盖</b>（管理员改过的流程不该被重装冲掉）；
 * 部署按 {@code processDefinitionKey + tenantId} 判重。流程表是业务表（查询只认本租户），
 * 故 pack.json 里 {@code reusable=false}：每个租户各装一份，各家可自行修改。
 */
@Component
public class ProcessInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(ProcessInstaller.class);

    /** 目录形态：设计器 DSL 文件后缀 */
    private static final String DSL_SUFFIX = ".dsl.json";
    /** 目录形态：BPMN 文件后缀（Flowable 约定名，{@code .bpmn20.xml} / {@code .bpmn} 都认） */
    private static final String BPMN_SUFFIX = ".bpmn20.xml";
    private static final String BPMN_ALT_SUFFIX = ".bpmn";

    /** 新建分类的排序权重（排在各租户自建分类之后，不抢位置） */
    private static final int CATEGORY_SORT = 100;

    private final ProcessTemplateService templateService;
    private final ProcessCategoryService categoryService;
    private final ProcessTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;

    public ProcessInstaller(ProcessTemplateService templateService,
                            ProcessCategoryService categoryService,
                            ProcessTemplateMapper templateMapper,
                            ObjectMapper objectMapper,
                            RepositoryService repositoryService) {
        this.templateService = templateService;
        this.categoryService = categoryService;
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
        this.repositoryService = repositoryService;
    }

    @Override
    public String resourceType() {
        return "PROCESS";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        if (isDirectory(resource)) {
            return installFromDirectory(context, resource);
        }
        return installFromSingleFile(context, resource);
    }

    // ==================== ① 目录形态 ====================

    /** {@code file} 指向包内目录（无扩展名，或以 {@code /} 结尾） */
    private boolean isDirectory(PackManifest.Resource resource) {
        String file = resource.getFile();
        if (file == null) {
            return false;
        }
        String trimmed = file.trim();
        return trimmed.endsWith("/") || !trimmed.contains(".");
    }

    private List<InstallResult> installFromDirectory(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        String dir = resource.getFile();

        // basename → [dslFile, bpmnFile]
        Map<String, String[]> pairs = new TreeMap<>();
        for (String name : context.getSource().list(dir)) {
            String base = null;
            int slot = 0;
            if (name.endsWith(DSL_SUFFIX)) {
                base = name.substring(0, name.length() - DSL_SUFFIX.length());
                slot = 0;
            } else if (name.endsWith(BPMN_SUFFIX)) {
                base = name.substring(0, name.length() - BPMN_SUFFIX.length());
                slot = 1;
            } else if (name.endsWith(BPMN_ALT_SUFFIX)) {
                base = name.substring(0, name.length() - BPMN_ALT_SUFFIX.length());
                slot = 1;
            } else {
                // 目录里混入了不认识的文件：如实报出来，别让它静默失效。
                // 例外：不含扩展名的条目是子目录（如 processes/forms/）—— 它是别的资源的家，
                // 不是"未识别的流程文件"，报出来只会变成噪音，静默跳过。
                if (name.contains(".")) {
                    results.add(InstallResult.skippedPending(resourceType(), dir + "/" + name,
                            "未识别的流程文件（只认 " + DSL_SUFFIX + " / " + BPMN_SUFFIX + "）"));
                }
                continue;
            }
            if (base == null || base.isEmpty()) {
                continue;
            }
            pairs.computeIfAbsent(base, k -> new String[2])[slot] = name;
        }
        if (pairs.isEmpty()) {
            results.add(InstallResult.skippedPending(resourceType(), dir,
                    "包内目录 " + dir + " 下没有流程文件（只认 " + DSL_SUFFIX + " / " + BPMN_SUFFIX + "）"));
            return results;
        }
        for (Map.Entry<String, String[]> entry : pairs.entrySet()) {
            String key = entry.getKey();
            String[] pair = entry.getValue();
            try {
                installOneProcess(context, results, key, dir, pair[0], pair[1]);
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), key,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("流程 {} 安装失败: {}", key, e.getMessage());
            }
        }
        log.debug("流程安装完成（目录形态 {}）: {} 条明细", dir, results.size());
        return results;
    }

    // ==================== ② 单文件形态（兼容） ====================

    private List<InstallResult> installFromSingleFile(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }
        Object processes = json.get("processes");
        if (!(processes instanceof List)) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "模板文件里没有 processes[] 数组（本包不含流程模板）"));
            return results;
        }
        for (Object element : (List<?>) processes) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String key = text(row.get("key"));
            if (key == null) {
                continue;
            }
            try {
                installFromRow(context, results, key, row, null);
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), key,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("流程模板 {} 安装失败: {}", key, e.getMessage());
            }
        }
        return results;
    }

    // ==================== 单个流程 ====================

    /**
     * 装一个流程：{@code dslFile} 与 {@code bpmnFile} 至少给一个（都相对包根）。
     * 两条路径的共同出口见 {@link #installFromRow}（单文件形态也走它，保证行为一致）。
     */
    private void installOneProcess(InstallContext context, List<InstallResult> results, String key,
                                   String dir, String dslFile, String bpmnFile) throws Exception {
        Map<String, Object> row = dslFile == null
                ? new java.util.LinkedHashMap<>()
                : context.getSource().readJsonMap(context.getObjectMapper(), dir + "/" + dslFile);
        if (row.isEmpty() && dslFile != null) {
            results.add(InstallResult.failed(resourceType(), dslFile, "读取 DSL 文件失败或内容为空"));
            return;
        }
        String bpmnXml = null;
        if (bpmnFile != null) {
            bpmnXml = new String(readAll(context, dir + "/" + bpmnFile), java.nio.charset.StandardCharsets.UTF_8);
        }
        installFromRow(context, results, key, row, bpmnXml);
    }

    /**
     * 落一个流程：先按需建模板（DSL），再按需部署（BPMN）。
     *
     * @param row     流程定义（{@code key/name/displayName/category/description/enabled/dsl}）；
     *                BPMN-only 时为空白，此时只部署、不建模板
     * @param bpmnXml 已编译好的 BPMN（没有则只建模板、不部署）
     */
    private void installFromRow(InstallContext context, List<InstallResult> results, String key,
                                Map<?, ?> row, String bpmnXml) throws Exception {
        String name = text(row.get("name")) == null ? key : text(row.get("name"));
        Object dsl = row.get("dsl");
        ProcessTemplate existing = templateMapper.selectByKey(key);

        // ---- A) BPMN-only：直接部署（等价内置流程的做法），如实说明"设计器里没有它" ----
        if (dsl == null && existing == null) {
            if (bpmnXml == null) {
                results.add(InstallResult.failed(resourceType(), key, "既没有 DSL 也没有 BPMN，装不了"));
                return;
            }
            if (context.isDryRun()) {
                results.add(InstallResult.inserted(resourceType(), key)
                        .why("预演：将直接部署 BPMN「" + name + "」（设计器里不会有模板，不能可视化编辑）"));
                return;
            }
            if (alreadyDeployed(key, context.getTenantOid())) {
                results.add(InstallResult.skipped(resourceType(), key, "BPMN 已部署（无模板）"));
                return;
            }
            deployDirect(key, name, bpmnXml, context.getTenantOid());
            results.add(InstallResult.inserted(resourceType(), key)
                    .why("已直接部署 BPMN「" + name + "」：装完即可发起；设计器里没有模板，不能可视化编辑")
                    .on("act_re_procdef", null));
            return;
        }

        if (dsl == null) {
            results.add(InstallResult.skipped(resourceType(), key, "模板已存在；本包只有 BPMN，未重复部署"));
            return;
        }

        int nodeCount = nodeCountOf(dsl);
        String dslJson = objectMapper.writeValueAsString(dsl);

        // ---- B) 模板已存在：不覆盖（管理员可能改过），仅当"从未部署且包里有 BPMN"时补一次部署 ----
        if (existing != null) {
            if (bpmnXml == null) {
                results.add(InstallResult.skipped(resourceType(), key, "流程模板已存在（未覆盖）"));
                return;
            }
            if (context.isDryRun()) {
                results.add(InstallResult.inserted(resourceType(), key)
                        .why("预演：模板已存在，将补部署 BPMN（装完即可发起）"));
                return;
            }
            if (existing.getDeployedVersion() != null || alreadyDeployed(key, context.getTenantOid())) {
                results.add(InstallResult.skipped(resourceType(), key, "流程模板已存在且已部署（未覆盖）"));
                return;
            }
            templateService.deploy(existing.getOid(), existing.getLatestVersion(), bpmnXml);
            results.add(InstallResult.inserted(resourceType(), key)
                    .why("流程模板已存在，补部署 BPMN：现在可以直接发起（模板内容未被覆盖）"));
            return;
        }

        // ---- C) 新建模板（+ 有 BPMN 就一并部署） ----
        if (context.isDryRun()) {
            results.add(InstallResult.inserted(resourceType(), key)
                    .why("预演：将新建流程模板「" + name + "」（" + nodeCount + " 个节点，分类「"
                            + text(row.get("category")) + "」）" + (bpmnXml == null
                            ? "；装好后需在设计器里「部署」才能发起"
                            : "，并用包内 BPMN 直接部署（装完即可发起）")));
            return;
        }
        String categoryOid = ensureCategory(text(row.get("category")));

        ProcessTemplate template = new ProcessTemplate();
        template.setKey(key);
        template.setName(name);
        template.setDisplayName(text(row.get("displayName")));
        template.setCategoryOid(categoryOid);
        template.setDescription(text(row.get("description")));
        template.setEnabled(row.get("enabled") == null || Boolean.TRUE.equals(row.get("enabled")));

        ProcessTemplate created = templateService.create(template, dslJson);
        if (bpmnXml == null) {
            results.add(InstallResult.inserted(resourceType(), key)
                    .why("流程模板：「" + name + "」（" + nodeCount + " 个节点）；需在设计器里「部署」后才能发起")
                    .on("ck_process_template", created.getOid()));
            return;
        }
        templateService.deploy(created.getOid(), created.getLatestVersion(), bpmnXml);
        results.add(InstallResult.inserted(resourceType(), key)
                .why("流程模板 + 已部署：「" + name + "」（" + nodeCount + " 个节点，分类「"
                        + categoryNameOf(categoryOid) + "」）—— 设计器可编辑，也可直接发起")
                .on("ck_process_template", created.getOid()));
    }

    /** BPMN 直部署（无模板场景，做法与内置流程 ProcessDeploymentSupport 一致：按租户各部署一份） */
    private void deployDirect(String key, String name, String bpmnXml, String tenantOid) {
        repositoryService.createDeployment()
                .name("行业包流程:" + name)
                .key(key)
                .tenantId(tenantOid)
                .addString(key + ".bpmn20.xml", bpmnXml)
                .deploy();
    }

    /** 该租户下这个流程 key 是否已部署 */
    private boolean alreadyDeployed(String key, String tenantOid) {
        try {
            return repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(key)
                    .processDefinitionTenantId(tenantOid)
                    .count() > 0;
        } catch (Exception e) {
            log.warn("查询流程部署状态失败（当作未部署处理）: {} - {}", key, e.getMessage());
            return false;
        }
    }

    /** 按名称找流程分类，没有就建一个（分类名租户内唯一，故按名匹配） */
    private String ensureCategory(String name) {
        String categoryName = (name == null || name.trim().isEmpty()) ? "行业流程" : name.trim();
        for (ProcessCategory category : categoryService.list()) {
            if (categoryName.equals(category.getName())) {
                return category.getOid();
            }
        }
        ProcessCategory created = categoryService.create(categoryName, CATEGORY_SORT, "由行业包装载");
        log.info("新建流程分类: {}（{}）", categoryName, created.getOid());
        return created.getOid();
    }

    private String categoryNameOf(String categoryOid) {
        try {
            ProcessCategory category = categoryService.requireByOid(categoryOid);
            return category == null ? "-" : category.getName();
        } catch (Exception e) {
            return "-";
        }
    }

    /** DSL 里的节点数（用于报告与预演，不必解析整个 DSL） */
    private int nodeCountOf(Object dsl) {
        if (!(dsl instanceof Map)) {
            return 0;
        }
        Object nodes = ((Map<?, ?>) dsl).get("nodes");
        return nodes instanceof List ? ((List<?>) nodes).size() : 0;
    }

    private byte[] readAll(InstallContext context, String relativeFile) throws Exception {
        try (java.io.InputStream in = context.getSource().open(relativeFile)) {
            return in.readAllBytes();
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
