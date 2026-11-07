/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.engine;

import cn.ck.plm.base.pack.install.InstallContext;
import cn.ck.plm.base.pack.install.InstallReport;
import cn.ck.plm.base.pack.install.InstallResult;
import cn.ck.plm.base.entity.TenantCapability;
import cn.ck.plm.base.mapper.TenantCapabilityMapper;
import cn.ck.plm.base.pack.capability.PlatformCapabilities;
import cn.ck.plm.base.pack.install.ResourceInstaller;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.base.pack.repo.IndustryPackRepository;
import cn.ck.plm.base.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 行业包安装引擎 —— 读清单、算依赖、按资源类型分派、幂等落库、记账。
 *
 * <h3>执行顺序（三层合并的实现）</h3>
 * <pre>
 * platform-base → electronics-core → smart-hardware-ipd（变体）→ 租户覆写（参数）
 * </pre>
 * 引擎按 {@code basePack} 递归先把父包装完，再装子包 —— 父包缺失是<b>硬错误</b>
 * （子包的软类型要绑父包的编码规则，顺序错了就会绑空）。
 *
 * <h3>三条铁律</h3>
 * <ol>
 *   <li><b>幂等</b>：每个 {@link ResourceInstaller} 按业务键判断，已存在即 SKIP（不覆盖用户改过的值）；
 *       包记录用 {@code UNIQUE(pack_code, pack_version, tenant_oid)} 兜底；</li>
 *   <li><b>预演不写</b>：{@link #plan} 与 {@link #install}(dryRun=true) 一行都不落库，
 *       只回答"你将得到什么"（引导页第 3 步靠它，用户可以反复点）；</li>
 *   <li><b>失败不阻断</b>：单条资源失败记 FAILED 继续；包级错误（版本区间不符、父包缺失）
 *       才整体不装。装不完的包状态记 PARTIAL，下次再装只补缺失项。</li>
 * </ol>
 *
 * <h3>租户与上下文</h3>
 * 调用 Installer 期间把 {@link TenantContext} 切到目标租户再还原：Installer 内部一律用现有
 * Service/Mapper 写业务表，租户归属由这一层统一负责，业务代码里不出现"为了装包而特判租户"。
 *
 * <h3>参数与升级</h3>
 * 装包时确定的参数（阶段工期、认定阈值、规则严重度…）写入
 * {@code ck_industry_pack.parameters_json}；重复安装/升级时<b>保留已存在值</b>
 * —— 那是租户自己的选择，包的新默认值不该覆盖它。
 */
@Component
public class IndustryPackEngine {

    private static final Logger log = LoggerFactory.getLogger(IndustryPackEngine.class);

    /** 平台版本（用于 engineRange 校验）：默认 0.3.0，可用 plm.platform-version 覆盖 */
    private final String platformVersion;

    private final IndustryPackLoader loader;
    private final IndustryPackRepository repository;
    private final ObjectMapper objectMapper;

    /** 资源类型 → 安装器（Spring 收集全部实现，键大小写不敏感） */
    private final Map<String, ResourceInstaller> installers = new HashMap<>();

    /** 租户能力账本：包里声明的、且平台已实现的能力，装包时落到 ck_tenant_capability */
    private final TenantCapabilityMapper capabilityMapper;

    /**
     * 按租户的真实安装单飞锁（dryRun 不参与）：安装是逐条自动提交的长事务，
     * 前端超时后用户重试会造成两个安装并发——各 Installer 的「先查后插」不是原子的，
     * 双方都判"不存在"就会双插撞唯一键。这里保证同一租户同一时刻只有一个安装在跑，
     * 并发请求直接快速失败（引擎幂等，稍后重试只会 SKIP 补齐）。
     */
    private final ConcurrentHashMap<String, ReentrantLock> tenantInstallLocks = new ConcurrentHashMap<>();

    public IndustryPackEngine(IndustryPackLoader loader,
                              IndustryPackRepository repository,
                              ObjectMapper objectMapper,
                              List<ResourceInstaller> installerList,
                              TenantCapabilityMapper capabilityMapper,
                              @Value("${plm.platform-version:0.3.0}") String platformVersion) {
        this.loader = loader;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.capabilityMapper = capabilityMapper;
        this.platformVersion = platformVersion.trim();
        if (installerList != null) {
            for (ResourceInstaller installer : installerList) {
                installers.put(installer.resourceType().toUpperCase(), installer);
            }
        }
        log.info("行业包引擎就绪：平台版本 {}，已注册 {} 个资源安装器 {}",
                this.platformVersion, installers.size(), installers.keySet());
    }

    // ==================== 对外入口 ====================

    /**
     * 安装包链（父包在前），可预演。
     *
     * @param packId    {@code code@version}
     * @param tenantOid 目标租户（平台级包传平台租户）
     * @param params    参数覆写（引导页可调项；空则用包默认值）
     * @param dryRun    true=只算不写
     * @return 按安装顺序返回报告（父包在前；调用方取最后一个是目标包）
     */
    public List<InstallReport> installChain(String packId, String tenantOid,
                                            Map<String, Object> params, boolean dryRun) {
        List<InstallReport> reports = new ArrayList<>();
        // 本链"已声明会装出"的各资源 code（父包在前累积，key = 资源类型大写：
        // SOFT_TYPE / STAGE / GATE / DELIVERABLE …）—— 供 Installer 预演时判前置依赖是否就绪
        //（如软类型判父类型、检查规则判被引用的阶段/门/交付件）
        Map<String, Set<String>> chainDeclaredCodes = new HashMap<>();
        if (dryRun) {
            installRecursive(packId, tenantOid, params, true, new HashSet<>(), reports, chainDeclaredCodes);
            return reports;
        }
        // 真实安装：按租户单飞，防止前端超时重试导致的并发双装（撞唯一键）
        ReentrantLock lock = tenantInstallLocks.computeIfAbsent(tenantOid, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new IllegalStateException("该租户正在安装行业解决方案，请勿重复提交，稍后在「已安装」中查看结果");
        }
        try {
            installRecursive(packId, tenantOid, params, false, new HashSet<>(), reports, chainDeclaredCodes);
        } finally {
            lock.unlock();
        }
        return reports;
    }

    /** 只看目标包的安装（不预演） */
    public InstallReport install(String packId, String tenantOid, Map<String, Object> params) {
        return lastOf(installChain(packId, tenantOid, params, false));
    }

    /** 预演：一行都不写，只回答"将会得到什么" */
    public InstallReport plan(String packId, String tenantOid, Map<String, Object> params) {
        return lastOf(installChain(packId, tenantOid, params, true));
    }

    /** 已安装记录（当前租户视角；平台级包也含在内） */
    public List<Map<String, Object>> listInstalled(String tenantOid) {
        List<Map<String, Object>> own = repository.listPacks(tenantOid);
        List<Map<String, Object>> platform =
                repository.listPacks(TenantContext.PLATFORM_TENANT_OID);
        List<Map<String, Object>> merged = new ArrayList<>(platform);
        for (Map<String, Object> row : own) {
            boolean duplicated = merged.stream().anyMatch(p ->
                    String.valueOf(p.get("pack_code")).equals(String.valueOf(row.get("pack_code")))
                            && String.valueOf(p.get("pack_version")).equals(String.valueOf(row.get("pack_version"))));
            if (!duplicated) {
                merged.add(row);
            }
        }
        return merged;
    }

    /** 全部可见包的校验结论（版本区间 / 父包是否存在 / 资源文件是否齐） */
    public List<Map<String, Object>> validateAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PackSource source : loader.loadAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("packId", source.getId());
            row.put("name", source.getManifest().getName());
            row.put("industry", source.getManifest().getIndustry());
            row.put("devMethod", source.getManifest().getDevMethod());
            row.put("targetScale", source.getManifest().getTargetScale());
            row.put("resources", source.getManifest().resourceCounts());
            row.put("contentHash", source.getManifest().getContentHash());

            List<String> problems = new ArrayList<>();
            String rangeError = checkEngineRange(source.getManifest());
            if (rangeError != null) {
                problems.add(rangeError);
            }
            String basePack = source.getManifest().getBasePackId();
            if (basePack != null && loader.find(basePack) == null) {
                problems.add("父包不存在: " + basePack);
            }
            // 资源文件是否存在（缺失只提示，不当作失败 —— 允许"分阶段填内容"的包）
            for (PackManifest.Resource resource : source.getManifest().getResources()) {
                if (resource.getFile() != null && !resource.getFile().endsWith("/")
                        && !source.exists(resource.getFile())) {
                    problems.add("资源文件缺失: " + resource.getFile());
                }
            }
            row.put("problems", problems);
            row.put("valid", problems.isEmpty());
            result.add(row);
        }
        return result;
    }

    /**
     * 包内容详情 —— <b>平台管理员"查看行业与方法论内容"</b>的只读投影。
     *
     * <p>它回答的问题是："这个行业包里到底装了什么方法？" 因此把三类东西摊开：
     * <ol>
     *   <li><b>清单元数据</b>：行业 / 方法论 / 适用规模 / 管控级别 / 主粒度 / 父包 / 版本区间 / 指纹；</li>
     *   <li><b>方法论叙事</b>：企业画像题（questionnaire）、可调参数与按规模的建议值（parameters）、
     *       内容亮点（previewHints）、装完后的待办（nextSteps）、示例数据声明（sample）、能力开关；</li>
     *   <li><b>资源逐条预览</b>：每个声明的模板文件 → 是否存在、能解析出多少条、条目明细（code/name…）
     *       —— P1 抽取完成后，这里会直接列出"IPD 六阶段叫什么""46 项交付物有哪些"。</li>
     * </ol>
     *
     * <p>只读：不写库、不触发安装（想看"真装会怎样"用 {@link #plan}）。
     */
    public Map<String, Object> contentDetail(PackSource source, String tenantOid) {
        PackManifest manifest = source.getManifest();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("packId", source.getId());
        detail.put("code", manifest.getCode());
        detail.put("version", manifest.getVersion());
        detail.put("name", manifest.getName());
        detail.put("industry", manifest.getIndustry());
        detail.put("devMethod", manifest.getDevMethod());
        detail.put("targetScale", manifest.getTargetScale());
        detail.put("controlLevel", manifest.getControlLevel());
        detail.put("license", manifest.getLicense());
        detail.put("primaryScopeType", manifest.getPrimaryScopeType());
        detail.put("basePack", manifest.getBasePack());
        detail.put("engineRange", manifest.getEngineRange());
        detail.put("platformVersion", platformVersion);
        detail.put("engineRangeSatisfied", checkEngineRange(manifest) == null);
        detail.put("contentHash", manifest.getContentHash());
        detail.put("author", manifest.getAuthor());
        detail.put("installCheck", manifest.getInstallCheck());
        detail.put("resourceCounts", manifest.resourceCounts());
        detail.put("questionnaire", manifest.getQuestionnaire());
        detail.put("parameters", manifest.getParameters());
        detail.put("previewHints", manifest.getPreviewHints());
        detail.put("nextSteps", manifest.getNextSteps());
        detail.put("sample", manifest.getSample());
        // 能力开关：逐条标注"平台是否已实现" —— 包声明是主张，注册表才是事实。
        // 不标这一下，界面就会把"包里写了"当成"系统有了"（与"假就绪"同款的坑）。
        List<Map<String, Object>> capabilityRows = new ArrayList<>();
        Object declaredCaps = source.getCapabilities().get("capabilities");
        if (declaredCaps instanceof List) {
            for (Object element : (List<?>) declaredCaps) {
                if (!(element instanceof Map)) {
                    continue;
                }
                Map<?, ?> def = (Map<?, ?>) element;
                String capCode = def.get("code") == null ? null : String.valueOf(def.get("code"));
                Map<String, Object> capRow = new LinkedHashMap<>();
                capRow.put("code", capCode);
                capRow.put("name", def.get("name"));
                capRow.put("default", def.get("default"));
                capRow.put("note", def.get("note"));
                capRow.put("implemented", PlatformCapabilities.isImplemented(capCode));
                capRow.put("implementedEvidence", PlatformCapabilities.evidenceOf(capCode));
                capabilityRows.add(capRow);
            }
        }
        detail.put("capabilities", capabilityRows);

        // 资源逐条预览（存在性 + 条目明细）
        List<Map<String, Object>> resources = new ArrayList<>();
        for (PackManifest.Resource resource : manifest.getResources()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", resource.getType());
            // 中文名给界面用（LIFECYCLE → 生命周期状态字典）；包没声明时回落类型码，界面不出现空白
            row.put("display", resource.getDisplay() == null ? resource.getType() : resource.getDisplay());
            row.put("file", resource.getFile());
            row.put("declaredCount", resource.getCount());
            row.put("scope", resource.getScope());
            List<Map<String, Object>> items = new ArrayList<>();
            String file = resource.getFile() == null ? "" : resource.getFile().trim();
            // 目录形态资源（file 指向包内目录：以 / 结尾，或不含扩展名，如 processes）：
            // 一个条目 = 一对文件（<key>.dsl.json + <key>.bpmn20.xml）。名字优先取 DSL 里的 name，
            // 读不出就回落文件名 —— 预览是给人看的辅助信息，不该因为一条读失败而让整页空掉。
            boolean directory = !file.isEmpty() && (file.endsWith("/") || !file.contains("."));
            boolean exists;
            if (directory) {
                final String dslSuffix = ".dsl.json";
                final String bpmnSuffix = ".bpmn20.xml";
                Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
                for (String name : source.list(file)) {
                    String key = null;
                    if (name.endsWith(dslSuffix)) {
                        key = name.substring(0, name.length() - dslSuffix.length());
                    } else if (name.endsWith(bpmnSuffix)) {
                        key = name.substring(0, name.length() - bpmnSuffix.length());
                    }
                    if (key == null || key.isEmpty()) {
                        continue;
                    }
                    Map<String, Object> item = byKey.computeIfAbsent(key, k -> {
                        Map<String, Object> created = new LinkedHashMap<>();
                        created.put("code", k);
                        created.put("name", k);
                        return created;
                    });
                    if (name.endsWith(dslSuffix)) {
                        try {
                            Map<String, Object> dsl = source.readJsonMap(objectMapper, file + "/" + name);
                            if (dsl.get("name") != null) {
                                item.put("name", dsl.get("name"));
                            }
                            item.put("category", dsl.get("category"));
                            item.put("note", dsl.get("displayName"));
                        } catch (Exception e) {
                            item.put("note", "读 DSL 失败（回落文件名显示）: " + e.getMessage());
                        }
                    }
                }
                items = new ArrayList<>(byKey.values());
                exists = !items.isEmpty();
            } else {
                exists = !file.isEmpty() && !file.endsWith("/") && source.exists(file);
                if (exists) {
                    try {
                        Map<String, Object> json = source.readJsonMap(objectMapper, file);
                        items = extractItems(json, 200);
                    } catch (Exception e) {
                        row.put("readError", e.getMessage());
                    }
                }
            }
            row.put("fileExists", exists);
            row.put("actualCount", items.isEmpty() ? null : items.size());
            row.put("items", items);
            resources.add(row);
        }
        detail.put("resources", resources);

        // 当前视角的安装记录（平台级 + 本租户）
        Map<String, Object> installed = repository.findPack(manifest.getCode(), manifest.getVersion(),
                TenantContext.PLATFORM_TENANT_OID);
        detail.put("installedAtPlatform", installed == null ? null : installed.get("install_state"));
        if (tenantOid != null && !TenantContext.PLATFORM_TENANT_OID.equals(tenantOid)) {
            Map<String, Object> own = repository.findPack(manifest.getCode(), manifest.getVersion(), tenantOid);
            detail.put("installedAtTenant", own == null ? null : own.get("install_state"));
        }
        return detail;
    }

    /** 条目行里保留的字段（够看清单，又不至于把大 JSON 原样丢给前端） */
    private static final String[] ENTRY_KEYS = {
            "code", "identifier", "slug", "name", "displayName", "type", "industry", "devMethod",
            "icon", "dataType", "sortOrder", "containerCode", "rootType", "stage", "discipline",
            "count", "mandatory", "severity", "seq", "gate"
    };

    /** 条目描述截断长度（描述里有长篇说明，整段塞进表格反而看不清） */
    private static final int NOTE_MAX = 90;

    /**
     * 从任意"行业包模板 JSON"里抽取条目摘要，供界面预览。
     *
     * <p><b>为什么不再按顶层键白名单取</b>：模板的顶层键是各资源自己的形状 ——
     * {@code softtypes.json} 是 {@code types}、{@code lifecycles.json} 是 {@code statuses} +
     * {@code lifecycleTemplates}、{@code stages.json} 是 {@code industries[].stages}（嵌套）、
     * 分类是 {@code classifications}（树）。早前那份键清单只覆盖到后两者，于是界面上
     * 只有分类与资源库两行能"查看 N 条"，其余全是"—"（平台管理员看不到清单细节）。
     * 改成<b>通用递归</b>：任何层级里"像条目"的对象都抽出来，模板换形状不用改这里。
     *
     * <p>判定"像条目"：有 {@code code}（或 {@code identifier}）→ 是；
     * 只有 {@code name} 且<b>不带嵌套的对象列表</b> → 也算（如 {@code statuses} 里的状态、
     * 资源库的根与子库）；否则视为<b>分组节点</b>（如 stages.json 的 industries），
     * 只往下钻、不占条目位 —— 这样"预览条目数"与清单声明的条数才大致对得上。
     */
    private List<Map<String, Object>> extractItems(Map<String, Object> json, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        walkEntries(json, items, limit, 0);
        return items;
    }

    private void walkEntries(Object node, List<Map<String, Object>> out, int limit, int depth) {
        if (node == null || out.size() >= limit || depth > 8) {
            return;
        }
        if (node instanceof List) {
            for (Object element : (List<?>) node) {
                if (out.size() >= limit) {
                    return;
                }
                walkEntries(element, out, limit, depth);
            }
            return;
        }
        if (!(node instanceof Map)) {
            return;
        }
        Map<?, ?> map = (Map<?, ?>) node;
        if (looksLikeEntry(map)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : ENTRY_KEYS) {
                Object value = map.get(key);
                if (value != null) {
                    row.put(key, value);
                }
            }
            Object description = map.get("description");
            if (description != null) {
                String note = String.valueOf(description);
                row.put("note", note.length() > NOTE_MAX ? note.substring(0, NOTE_MAX) + "…" : note);
            }
            out.add(row);
        }
        for (Object value : map.values()) {
            if (out.size() >= limit) {
                return;
            }
            walkEntries(value, out, limit, depth + 1);
        }
    }

    /**
     * 有 code/identifier/slug <b>且带名字</b>，或只有 name 且不含嵌套对象列表（分组节点不算条目）。
     *
     * <p>那个"且带名字"是后补的：只认 code 会把<b>配置对象</b>也算成条目 —— 典型是
     * 检查规则里的 {@code fixAction: {type, target, code}}（它指一个交付件 code），
     * 于是清单里冒出一行没有名字的裸码，且"解析条数"比声明多一。条目至少该有个给人看的名字。
     */
    private boolean looksLikeEntry(Map<?, ?> map) {
        if (map.get("code") != null || map.get("identifier") != null || map.get("slug") != null) {
            return map.get("name") != null || map.get("displayName") != null;
        }
        if (map.get("name") == null) {
            return false;
        }
        for (Object value : map.values()) {
            if (value instanceof List) {
                for (Object element : (List<?>) value) {
                    if (element instanceof Map) {
                        return false;   // 有嵌套对象列表 → 更像分组，不占条目位
                    }
                }
            }
        }
        return true;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    // ==================== 递归安装 ====================

    private void installRecursive(String packId, String tenantOid, Map<String, Object> params,
                                  boolean dryRun, Set<String> visited, List<InstallReport> reports,
                                  Map<String, Set<String>> chainDeclaredCodes) {
        installRecursive(packId, tenantOid, params, dryRun, visited, reports, false, chainDeclaredCodes);
    }

    /**
     * @param asDependency       true = 这次是为满足别人（父包）而来的，不是调用方点名要装的
     * @param chainDeclaredCodes 本链"已声明会装出"的各资源 code（父包在前累积，key = 资源类型大写）——
     *                           供 Installer 在**预演**时判"前置依赖是否就绪"
     *                           （见 {@code InstallContext#getChainDeclaredCodes}）
     */
    private void installRecursive(String packId, String tenantOid, Map<String, Object> params,
                                  boolean dryRun, Set<String> visited, List<InstallReport> reports,
                                  boolean asDependency, Map<String, Set<String>> chainDeclaredCodes) {
        if (packId == null || !visited.add(packId)) {
            return;
        }
        PackSource source = loader.find(packId);
        if (source == null) {
            InstallReport report = new InstallReport();
            report.setPackId(packId);
            report.setTenantOid(tenantOid);
            report.setDryRun(dryRun);
            report.setInstallState("FAILED");
            report.error("包不存在: " + packId);
            reports.add(report);
            return;
        }

        // 平台级已装好的"基"层包：**只有可共享的资源**能在租户级复用，其余必须真的装进本租户。
        //
        // 为什么不能整包复用：一张表的可见性由 TenantStatementInterceptor 决定 ——
        //   · 共享表（ck_type_definition / ck_lifecycle_* / ck_number / ck_version_rule / ck_view …）：
        //     查询注入 tenant_oid IN (平台, 本租户) → 平台那份本租户天然可见 → 可复用；
        //   · 其余表（ck_classification / ck_cls_iba / ck_iba / ck_resource_container / ck_stage /
        //     ck_stage_template …）：查询注入 tenant_oid = '本租户' → 平台那份**本租户根本看不见**
        //     → 必须装自己一份。
        // 曾经是整包复用，并据此在引导清单上写"全租户共享，本企业直接复用" —— 结果租户装完既没有
        // 分类树也没有资源库，界面上却显示"已就绪"：假就绪比没装更糟（管理员不会再去补）。
        // 能否复用的声明在各包 pack.json 的 resources[].reusable（缺省 false：不确定就装一份）。
        boolean reusePlatform = asDependency
                && !TenantContext.PLATFORM_TENANT_OID.equals(tenantOid)
                && isInstalledAtPlatform(source);

        // 1) 父包先装（三层合并：基 → 变体）
        String basePack = source.getManifest().getBasePackId();
        if (basePack != null) {
            installRecursive(basePack, tenantOid, params, dryRun, visited, reports, true, chainDeclaredCodes);
        }
        // 1.5) 把本包**声明**的各资源 code 收进链上集合（父包先加、本包随后、子包再读）：
        //      预演时"前置依赖是否就绪"就看它 —— 依据是包声明，与库状态、与平台租户都无关。
        collectDeclaredCodes(source, chainDeclaredCodes);

        // 2) 自身
        boolean hasOwnResource = source.getManifest().getResources().stream()
                .anyMatch(r -> !isReusable(r));
        if (reusePlatform && !hasOwnResource) {
            log.info("行业包 {} 已在平台级安装，且其资源全部平台共享 —— 租户 {} 直接复用", packId, tenantOid);
            // 仍然出一份"复用"报告：引导页的初始化清单要能看到"基座 → 共用基 → 行业包"三层递进，
            // 若这里直接 return，清单上只剩行业包自己那几条，管理员看不出它建立在什么之上。
            InstallReport reused = reuseReport(source, tenantOid, dryRun, basePack);
            // 资源可以复用，能力不能：包声明的能力仍要落到<b>本租户</b>，否则租户装了 IPD 却没有 IPD 的能力。
            // 这条快路不经过 installOne（那里的租户切换也就没发生），故这里自己切一次再还原。
            String previousTenant = TenantContext.getOrNull();
            TenantContext.set(tenantOid);
            try {
                applyCapabilities(source, tenantOid, dryRun, reused);
            } finally {
                if (previousTenant == null) {
                    TenantContext.clear();
                } else {
                    TenantContext.set(previousTenant);
                }
            }
            reports.add(reused);
            return;
        }
        reports.add(installOne(source, tenantOid, params, dryRun, basePack, reusePlatform, chainDeclaredCodes));
    }

    /**
     * 收集包内**各类可被依赖的资源**声明的 code（只读包声明，不解析别的）。
     *
     * <p>键名因资源类型而异：{@code SOFT_TYPE}（{@code types[].code}）、
     * {@code STAGE}（{@code industries[].stages[].code}）、
     * {@code GATE} / {@code DELIVERABLE}（{@code items[].code}）。
     *
     * <p>为什么需要它：预演一行都不写库，本租户必然还没有基础包装出来的东西 —— 判"前置依赖是否就绪"
     * 只能依据<b>包声明</b>（本链前面的包声明过 = 真装时会装进本租户）。不依据"平台租户恰好有一份"：
     * 平台级不注册业务对象，那个前提迟早失效。
     *
     * <p>失败只告警不抛：包是数据，读不动不该让装包挂掉；表现为预演里少数依赖判"未就绪"，
     * 比整场预演失败更好定位。
     */
    private void collectDeclaredCodes(PackSource source, Map<String, Set<String>> into) {
        if (source == null || source.getManifest() == null || source.getManifest().getResources() == null) {
            return;
        }
        for (PackManifest.Resource resource : source.getManifest().getResources()) {
            String type = resource.getType() == null ? null : resource.getType().toUpperCase();
            if (type == null || resource.getFile() == null) {
                continue;
            }
            if (!"SOFT_TYPE".equals(type) && !"STAGE".equals(type)
                    && !"GATE".equals(type) && !"DELIVERABLE".equals(type)) {
                continue;
            }
            try {
                Map<String, Object> json = source.readJsonMap(objectMapper, resource.getFile());
                Set<String> target = into.computeIfAbsent(type, k -> new HashSet<>());
                for (String code : extractDeclaredCodes(type, json)) {
                    if (code != null && !code.trim().isEmpty()) {
                        target.add(code.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("读取 {} 的 {} 声明失败（预演时依赖判定会偏保守）: {}",
                        source.getId(), type, e.getMessage());
            }
        }
    }

    /** 从包内文件结构中取出声明的 code（各资源类型的键名不同，与对应安装器的读法保持一致） */
    private List<String> extractDeclaredCodes(String type, Map<String, Object> json) {
        List<String> codes = new ArrayList<>();
        if ("SOFT_TYPE".equals(type)) {
            Object types = json.get("types");
            if (types instanceof List) {
                for (Object element : (List<?>) types) {
                    if (element instanceof Map) {
                        Object code = ((Map<?, ?>) element).get("code");
                        if (code != null) {
                            codes.add(String.valueOf(code));
                        }
                    }
                }
            }
        } else if ("STAGE".equals(type)) {
            Object industries = json.get("industries");
            if (industries instanceof List) {
                for (Object industryRow : (List<?>) industries) {
                    if (!(industryRow instanceof Map)) {
                        continue;
                    }
                    Object stages = ((Map<?, ?>) industryRow).get("stages");
                    if (stages instanceof List) {
                        for (Object stageRow : (List<?>) stages) {
                            if (stageRow instanceof Map) {
                                Object code = ((Map<?, ?>) stageRow).get("code");
                                if (code != null) {
                                    codes.add(String.valueOf(code));
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // GATE / DELIVERABLE：items[].code
            Object items = json.get("items");
            if (items instanceof List) {
                for (Object element : (List<?>) items) {
                    if (element instanceof Map) {
                        Object code = ((Map<?, ?>) element).get("code");
                        if (code != null) {
                            codes.add(String.valueOf(code));
                        }
                    }
                }
            }
        }
        return codes;
    }

    /** 该包是否已在平台租户装好（非 FAILED 即视为可用副本） */
    private boolean isInstalledAtPlatform(PackSource source) {
        try {
            Map<String, Object> row = repository.findPack(source.getManifest().getCode(),
                    source.getManifest().getVersion(), TenantContext.PLATFORM_TENANT_OID);
            if (row == null || row.get("install_state") == null) {
                return false;
            }
            return !"FAILED".equals(String.valueOf(row.get("install_state")));
        } catch (Exception e) {
            log.warn("查询平台级安装记录失败（当作未安装处理）: {}", source.getId());
            return false;
        }
    }

    /**
     * 该资源能否直接复用平台副本 —— 看包清单 {@code resources[].reusable}。
     *
     * <p>缺省（未声明 / false）一律按"装自己一份"处理：宁可多写一份租户数据，也不能让租户
     * 对着清单上的"已就绪"却读不到东西。声明 true 的前提是该资源落的表在
     * {@code TenantStatementInterceptor} 的共享名单里（查询注入"平台 + 本租户"）。
     */
    private boolean isReusable(PackManifest.Resource resource) {
        return Boolean.TRUE.equals(resource.getReusable());
    }

    /**
     * 复用平台副本的报告（不写库、不记明细）：逐条资源给出"平台已配置 N 条，系统级共享"，
     * 动作记为 SKIP —— 站在本租户角度，这些内容确实"已经在那儿且读得到"。
     * 装完后它不会进入明细表：复用不是一次安装。
     *
     * <p>只在该包资源<b>全部</b>可共享时走这条快路（见 {@code installRecursive}）；
     * 只要有一条得装进租户，就走 {@code installOne} 的逐资源判定。
     */
    private InstallReport reuseReport(PackSource source, String tenantOid, boolean dryRun, String basePackId) {
        PackManifest manifest = source.getManifest();
        InstallReport report = new InstallReport();
        report.setPackId(source.getId());
        report.setPackName(manifest.getName());
        report.setTenantOid(tenantOid);
        report.setDryRun(dryRun);
        report.setBasePackId(basePackId);
        report.setContentHash(manifest.getContentHash());
        report.setPlatformVersion(platformVersion);
        report.setInstallState("REUSED");
        for (PackManifest.Resource resource : manifest.getResources()) {
            String key = resource.getFile() == null ? manifest.getId() : resource.getFile();
            String why = resource.getCount() == null
                    ? "平台已配置，系统级共享（本企业直接使用，不重复写入）"
                    : "平台已配置 " + resource.getCount() + " 条，系统级共享（本企业直接使用，不重复写入）";
            report.add(InstallResult.skipped(resource.getType(), key, why).on(null, null));
        }
        return report;
    }

    /**
     * 把包声明的能力"落账"到目标租户 —— 只落<b>平台已实现</b>的（见 {@link PlatformCapabilities}）。
     *
     * <p>这是"包声明"与"系统事实"的接合点：{@code capabilities.json} 是主张，{@code ck_tenant_capability}
     * 是事实。包里声明而平台未实现的（如 CHANGE_MANAGEMENT）记 SKIP + 原因，<b>不算失败</b> ——
     * 包装得上，但要如实告诉管理员"这一项平台还没有"（否则界面标着已启用、用户却找不到入口）。
     *
     * <p>幂等：已存在的记 SKIP，不覆盖（管理员手动关掉的能力，不该被下一次装包重新打开）。
     * 预演只报"将启用"，不写库。
     *
     * <p>调用方必须已把 {@link TenantContext} 切到目标租户 —— 这张表是业务表，
     * SELECT 会被拦截器加上 {@code tenant_oid = 当前租户}，上下文不对就会误判"未启用"。
     */
    private void applyCapabilities(PackSource source, String tenantOid, boolean dryRun, InstallReport report) {
        Object node = source.getCapabilities().get("capabilities");
        if (!(node instanceof List)) {
            return;
        }
        for (Object element : (List<?>) node) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> def = (Map<?, ?>) element;
            if (def.get("code") == null) {
                continue;
            }
            String code = String.valueOf(def.get("code"));
            String name = def.get("name") == null ? code : String.valueOf(def.get("name"));
            try {
                if (!PlatformCapabilities.isImplemented(code)) {
                    report.add(InstallResult.skippedPending("CAPABILITY", code,
                            "本包声明「" + name + "」但平台当前未实现，未启用（平台补齐后重装即生效）"));
                    continue;
                }
                if (Boolean.FALSE.equals(def.get("default"))) {
                    report.add(InstallResult.skippedPending("CAPABILITY", code,
                            "本包登记「" + name + "」为默认不启用"));
                    continue;
                }
                if (dryRun) {
                    report.add(InstallResult.inserted("CAPABILITY", code)
                            .why("预演：将为该租户启用能力「" + name + "」"));
                    continue;
                }
                if (capabilityMapper.exists(tenantOid, code) > 0) {
                    report.add(InstallResult.skipped("CAPABILITY", code, "本租户已启用「" + name + "」"));
                    continue;
                }
                TenantCapability row = new TenantCapability();
                row.setOid(java.util.UUID.randomUUID().toString());
                row.setTenantOid(tenantOid);
                row.setCapabilityCode(code);
                row.setCapabilityName(name);
                row.setEnabled(true);
                row.setSourcePack(source.getId());
                row.setNote(def.get("note") == null ? null : String.valueOf(def.get("note")));
                row.setCreator("system");
                row.setUpdater("system");
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                capabilityMapper.insert(row);
                report.add(InstallResult.inserted("CAPABILITY", code)
                        .why("启用能力「" + name + "」（来自 " + source.getId() + "）")
                        .on("ck_tenant_capability", row.getOid()));
            } catch (Exception e) {
                // 单个能力失败不阻断装包，但必须如实记（界面要能看到"哪一项没落上"）
                report.add(InstallResult.failed("CAPABILITY", code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private InstallReport installOne(PackSource source, String tenantOid, Map<String, Object> params,
                                     boolean dryRun, String basePackId, boolean reusePlatform,
                                     Map<String, Set<String>> chainDeclaredCodes) {
        PackManifest manifest = source.getManifest();
        InstallReport report = new InstallReport();
        report.setPackId(source.getId());
        report.setPackName(manifest.getName());
        report.setTenantOid(tenantOid);
        report.setDryRun(dryRun);
        report.setBasePackId(basePackId);
        report.setContentHash(manifest.getContentHash());
        report.setPlatformVersion(platformVersion);

        // a) 版本区间
        String rangeError = checkEngineRange(manifest);
        if (rangeError != null) {
            report.setInstallState("FAILED");
            report.error(rangeError);
            return report;
        }

        // b) 已有记录（幂等 + 参数保留）
        Map<String, Object> existing = dryRun ? null
                : repository.findPack(manifest.getCode(), manifest.getVersion(), tenantOid);
        Map<String, Object> effectiveParams = mergeParameters(existing, params);
        String packOid = existing == null ? null : String.valueOf(existing.get("oid"));

        if (!dryRun && existing == null) {
            packOid = repository.insertPack(manifest.getCode(), manifest.getVersion(), basePackId,
                    manifest.getEngineRange(), manifest.getIndustry(), manifest.getDevMethod(),
                    manifest.getTargetScale(), "INSTALLED", manifest.getContentHash(),
                    toJson(effectiveParams), "system", tenantOid);
        }

        // c) 逐条资源分派
        String previousTenant = TenantContext.getOrNull();
        TenantContext.set(tenantOid);
        try {
            InstallContext context = new InstallContext(source, tenantOid, dryRun, effectiveParams,
                    objectMapper, chainDeclaredCodes);
            for (PackManifest.Resource resource : manifest.getResources()) {
                // 平台级已装过、且该资源可共享 → 本租户直接复用平台那份（不写库，只记明细）
                if (reusePlatform && isReusable(resource)) {
                    String reuseKey = resource.getFile() == null ? manifest.getId() : resource.getFile();
                    String reuseWhy = resource.getCount() == null
                            ? "平台已配置，系统级共享（本企业直接使用，不重复写入）"
                            : "平台已配置 " + resource.getCount() + " 条，系统级共享（本企业直接使用，不重复写入）";
                    report.add(InstallResult.skipped(resource.getType(), reuseKey, reuseWhy).on(null, null));
                    continue;
                }
                ResourceInstaller installer = resource.getType() == null ? null
                        : installers.get(resource.getType().toUpperCase());
                if (installer == null) {
                    // 尚无安装器：明确记下"跳过 + 原因"，而不是假装装好了。
                    // 用 skippedPending：这不是"已就绪"（东西压根没装），界面应显示"跳过"。
                    InstallResult skipped = InstallResult.skippedPending(
                            resource.getType() == null ? "UNKNOWN" : resource.getType(),
                            resource.getFile() == null ? manifest.getId() : resource.getFile(),
                            "尚无安装器（待后续版本补齐）");
                    skipped.on(null, null);
                    report.add(skipped);
                    log.warn("行业包 {} 的资源 {} 暂无安装器，跳过", source.getId(), resource.getType());
                    continue;
                }
                try {
                    List<InstallResult> results = installer.install(context, resource);
                    if (results != null) {
                        results.forEach(report::add);
                    }
                } catch (Exception e) {
                    // 单条资源失败不阻断整体
                    log.warn("行业包 {} 的资源 {} 安装失败: {}", source.getId(), resource.getType(), e.getMessage());
                    report.add(InstallResult.failed(
                            resource.getType(), resource.getFile() == null ? manifest.getId() : resource.getFile(),
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }
            // 资源装完，接着把包声明的能力落到本租户（只落平台已实现的；未实现的记 SKIP + 原因）
            applyCapabilities(source, tenantOid, dryRun, report);
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }

        // d) 状态与记账
        List<InstallResult> failures = InstallResult.failures(report.getResults());
        String state = failures.isEmpty() ? "INSTALLED"
                : (report.getResults().size() == failures.size() ? "FAILED" : "PARTIAL");
        if (!dryRun) {
            report.setInstallState(state);
            if (existing != null) {
                repository.updatePackState(packOid, state, manifest.getContentHash(), toJson(effectiveParams));
            }
            for (InstallResult result : report.getResults()) {
                repository.insertResource(packOid, result, tenantOid);
            }
        } else {
            report.setInstallState("PLANNED");
        }

        log.info("行业包{}: {}", dryRun ? "（预演）" : "", report.toSummaryLine());
        return report;
    }

    // ==================== 辅助 ====================

    /** 已存在的参数优先（租户覆写不被包默认值覆盖），其余补入本次覆写 */
    private Map<String, Object> mergeParameters(Map<String, Object> existing, Map<String, Object> overrides) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existing != null && existing.get("parameters_json") != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = objectMapper.readValue(
                        String.valueOf(existing.get("parameters_json")), Map.class);
                merged.putAll(stored);
            } catch (Exception e) {
                log.warn("解析已存参数失败: {}", e.getMessage());
            }
        }
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return merged;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    /** engineRange 校验：支持 {@code >=0.2.0 <1.0.0} 这类空格分隔的区间；空表示不限 */
    private String checkEngineRange(PackManifest manifest) {
        String range = manifest.getEngineRange();
        if (range == null || range.trim().isEmpty()) {
            return null;
        }
        for (String token : range.trim().split("\\s+")) {
            if (!satisfiesToken(token, platformVersion)) {
                return "平台版本 " + platformVersion + " 不满足包的 engineRange: " + range;
            }
        }
        return null;
    }

    private boolean satisfiesToken(String token, String version) {
        String operator = "=";
        String value = token;
        for (String candidate : new String[]{">=", "<=", ">", "<", "="}) {
            if (token.startsWith(candidate)) {
                operator = candidate;
                value = token.substring(candidate.length()).trim();
                break;
            }
        }
        int cmp = compareVersion(version, value);
        switch (operator) {
            case ">=": return cmp >= 0;
            case ">":  return cmp > 0;
            case "<=": return cmp <= 0;
            case "<":  return cmp < 0;
            default:   return cmp == 0;
        }
    }

    /** 语义化版本比较（只比较数字段；非法段按 0 处理，避免因一个包的写法问题阻断安装） */
    static int compareVersion(String left, String right) {
        int[] a = numbers(left);
        int[] b = numbers(right);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] numbers(String version) {
        if (version == null) {
            return new int[0];
        }
        String[] parts = version.trim().split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9].*$", "");
            try {
                result[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static InstallReport lastOf(List<InstallReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        return reports.get(reports.size() - 1);
    }

    /** 供外部（引导页/平台侧）查询某包是否已装到某租户 */
    public boolean isInstalled(String packCode, String tenantOid) {
        return repository.isInstalled(packCode, tenantOid);
    }

    /** 空参数（避免调用方传 null） */
    public static Map<String, Object> noParams() {
        return Collections.emptyMap();
    }
}
