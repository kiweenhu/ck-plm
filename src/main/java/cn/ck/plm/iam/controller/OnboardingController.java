/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.base.pack.engine.IndustryPackEngine;
import cn.ck.plm.base.pack.install.InstallReport;
import cn.ck.plm.base.pack.install.InstallResult;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.base.service.api.StageTemplateService;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.TenantMapper;
import cn.ck.plm.iam.service.api.TenantService;
import cn.ck.plm.iam.service.api.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业初始化引导（租户管理员首次登录）—— <b>先选行业，方法论是它的结果</b>。
 *
 * <h3>为什么需要它</h3>
 * 审核通过只回答"租户被允许使用"，没回答"用它来做什么"。行业与研发方法论是后者：
 * 决定采用哪套研发阶段（IPD 六阶段 / APQP / 军工定型 / 阶段门 …），进而决定分类树、
 * 编号规则、交付物与评审的落地方式。<b>放在租户管理员首登时自选</b>：
 * <ul>
 *   <li>平台管理员只做准入（审核），不替租户做技术选型；</li>
 *   <li>答案由最懂业务的人（租户管理员）给出，且当场就能看到"我将得到什么"。</li>
 * </ul>
 *
 * <h3>状态机（与 schema.sql 的注释一致）</h3>
 * <pre>
 * ck_tenant.status            ：PENDING → ACTIVE（平台审核）
 * ck_tenant.onboarding_state  ：PENDING → DONE（租户管理员首登引导，本类负责）
 * </pre>
 * 两条线正交：审核通过 ≠ 引导完成。引导未完成时：管理员被强制进引导页，普通成员看到等待页
 * （前端按 {@code state} 的 {@code needsOnboarding} + {@code isTenantAdmin} 判定）。
 *
 * <h3>选项从哪来：行业包（不是硬编码清单）</h3>
 * 一个包在 {@code pack.json} 里同时声明了 {@code industry} 与 {@code devMethod}，它就是一个
 * <b>可选行业</b>；{@code platform-base}（无行业身份）与 {@code electronics-core}（行业共用基、
 * 无语义方法论）自然不在候选里。<b>行业是主选项，方法论是该行业包给的结果</b> ——
 * 换行业=换包，六阶段连同将来的门/交付物/规则一起走，不必改代码。
 *
 * <h3>选定后做什么</h3>
 * <ol>
 *   <li><b>按该租户安装选中的行业包</b>（{@code engine.installChain(packId, tenantOid, …)}）——
 *       阶段模板落成<b>租户级</b>行：租户能按本企业改（加评审点、改名称），改了不影响别的租户；</li>
 *   <li>引擎对已在平台级装好的<b>父包</b>（platform-base / electronics-core 是"基"层）<b>逐资源</b>判定：
 *       平台共享类的资源（生命周期 / 编码与版本规则 / 视图）直接复用，不重复写；
 *       租户级隔离的资源（分类树 / 计量单位 / **类型定义与其属性·页面布局·规则绑定** / 分类参数 /
 *       企业资源库 / 阶段模板）<b>照常装进本租户</b> ——
 *       它们的读写都限定在本租户（分类树靠拦截器 / 显式条件，单位自 2026-10-10 起同样隔离），
 *       平台那份本租户读不到，整包跳过会让租户装完仍没有分类树与资源库；</li>
 *   <li>记下选择：{@code industry_code / dev_method_code / industry_pack_code / onboarded_*}。</li>
 * </ol>
 *
 * <p><b>行业一经选定不可改</b>（要换只能新开租户）—— 因为它已经落成了分类、阶段与编号规则，
 * "改"等于要求系统做一次跨域数据迁移，那是另一个量级的事。
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    /** 租户管理员角色 code（与 TenantServiceImpl 保持一致） */
    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final TenantService tenantService;
    private final TenantMapper tenantMapper;
    private final UserService userService;
    private final StageTemplateService stageTemplateService;
    private final IndustryPackLoader packLoader;
    private final IndustryPackEngine packEngine;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public OnboardingController(TenantService tenantService,
                                TenantMapper tenantMapper,
                                UserService userService,
                                StageTemplateService stageTemplateService,
                                IndustryPackLoader packLoader,
                                IndustryPackEngine packEngine,
                                ObjectMapper objectMapper,
                                JdbcTemplate jdbcTemplate) {
        this.tenantService = tenantService;
        this.tenantMapper = tenantMapper;
        this.userService = userService;
        this.stageTemplateService = stageTemplateService;
        this.packLoader = packLoader;
        this.packEngine = packEngine;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 引导状态 —— 前端登录后的路由守卫据此决定：进引导页 / 进等待页 / 正常进系统。
     */
    @GetMapping("/state")
    public ApiResponse<Map<String, Object>> state() {
        String username = UserContext.get();
        if (username == null) {
            return ApiResponse.fail(401, "未登录");
        }
        try {
            User user = userService.findByUsername(username);
            if (user == null || user.getTenantOid() == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            Tenant tenant = tenantService.getByOid(user.getTenantOid());
            if (tenant == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tenantOid", tenant.getOid());
            data.put("tenantId", tenant.getTenantId());
            data.put("tenantName", tenant.getName());
            data.put("tenantStatus", tenant.getStatus());
            data.put("onboardingState", tenant.getOnboardingState());
            data.put("onboarded", tenant.isOnboarded());
            data.put("industryCode", tenant.getIndustryCode());
            data.put("devMethodCode", tenant.getDevMethodCode());
            data.put("industryPackCode", tenant.getIndustryPackCode());
            data.put("onboardedAt", tenant.getOnboardedAt());
            data.put("onboardedBy", tenant.getOnboardedBy());
            data.put("isTenantAdmin", isTenantAdmin(user.getOid()));
            data.put("optionCount", listIndustryOptions().size());
            try {
                data.put("tenantStageCount", stageTemplateService.findAll(null).size());
            } catch (Exception e) {
                data.put("tenantStageCount", 0);
            }
            data.put("needsOnboarding", !tenant.isOnboarded());
            data.put("waitingHint", tenant.isOnboarded() ? null
                    : "本企业尚未完成行业初始化，请联系企业管理员在首次登录时选定行业与研发方法。");
            return ApiResponse.ok(data);
        } catch (Exception e) {
            log.error("查询引导状态失败: user={}", username, e);
            return ApiResponse.fail(500, "查询引导状态失败");
        }
    }

    /**
     * 可选项 —— <b>行业</b>（方法论是它的结果）。数据来自行业包清单：
     * 同时声明了 {@code industry} 与 {@code devMethod} 的包才算一个可选行业。
     */
    @GetMapping("/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        if (UserContext.get() == null) {
            return ApiResponse.fail(401, "未登录");
        }
        try {
            return ApiResponse.ok(listIndustryOptions());
        } catch (Exception e) {
            log.error("查询引导选项失败", e);
            return ApiResponse.fail(500, "查询引导选项失败");
        }
    }

    /**
     * 完成引导 —— 选定行业（方法论随之确定），并按本租户安装该行业包。
     *
     * <p>权限：仅<b>本租户</b>的租户管理员可执行；只能给自己租户引导（不接受 tenantOid 参数）。
     * 幂等性：已完成（{@code onboarding_state=DONE}）时拒绝再次执行 —— 行业不可改。
     */
    @PostMapping("/complete")
    public ApiResponse<Map<String, Object>> complete(@RequestBody Map<String, Object> body) {
        String username = UserContext.get();
        if (username == null) {
            return ApiResponse.fail(401, "未登录");
        }
        String industryCode = body == null ? null : trim(body.get("industryCode"));
        String devMethodCode = body == null ? null : trim(body.get("devMethodCode"));
        String profileJson = body == null ? null : trim(body.get("profile"));
        if (industryCode == null) {
            return ApiResponse.fail(400, "请先选择行业");
        }
        try {
            User user = userService.findByUsername(username);
            if (user == null || user.getTenantOid() == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            if (!isTenantAdmin(user.getOid())) {
                return ApiResponse.fail(403, "仅企业管理员可完成行业初始化");
            }
            Tenant tenant = tenantService.getByOid(user.getTenantOid());
            if (tenant == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            if (!tenant.isActive()) {
                return ApiResponse.fail(400, "租户状态为 " + tenant.getStatus() + "，暂不能进行行业初始化");
            }
            if (tenant.isOnboarded()) {
                return ApiResponse.fail(400, "行业已完成初始化（" + tenant.getIndustryCode()
                        + "），一经选定不可修改；如需更换请新建租户");
            }

            PackSource chosen = findIndustryPack(industryCode, devMethodCode);
            if (chosen == null) {
                return ApiResponse.fail(400, "找不到可用的行业包: " + industryCode
                        + "（可能尚未随平台发布，或该包/其基包未启用）");
            }

            // 按租户安装：阶段等"变体"内容落租户级；父包（平台级已装）由引擎自动跳过
            List<InstallReport> reports = packEngine.installChain(chosen.getId(), tenant.getOid(),
                    IndustryPackEngine.noParams(), false);
            InstallReport last = reports.isEmpty() ? null : reports.get(reports.size() - 1);
            int detailCount = last == null ? 0 : last.getResults().size();
            List<String> errors = last == null ? new ArrayList<>() : last.getErrors();
            if (!errors.isEmpty()) {
                log.warn("行业包 {} 装到租户 {} 时有错误: {}", chosen.getId(), tenant.getOid(), errors);
            }

            tenant.setIndustryCode(industryCode);
            tenant.setDevMethodCode(chosen.getManifest().getDevMethod());
            tenant.setIndustryPackCode(chosen.getId());
            tenant.setOnboardingState(Tenant.ONBOARDING_DONE);
            tenant.setOnboardedAt(LocalDateTime.now());
            tenant.setOnboardedBy(username);
            if (profileJson != null) {
                tenant.setProfileJson(profileJson);
            }
            tenantMapper.update(tenant);

            Map<String, Object> option = describeIndustryOption(chosen);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("industryCode", industryCode);
            data.put("devMethodCode", tenant.getDevMethodCode());
            data.put("packId", chosen.getId());
            data.put("packName", chosen.getManifest().getName());
            data.put("stageCount", option == null ? 0 : option.get("stageCount"));
            data.put("detailCount", detailCount);
            data.put("errors", errors);
            data.put("onboardedAt", tenant.getOnboardedAt());
            data.put("onboardedBy", username);
            log.info("行业初始化完成: tenantId={}, 行业={}, 方法论={}, 包={}, 明细 {} 条, by={}",
                    tenant.getTenantId(), industryCode, tenant.getDevMethodCode(), chosen.getId(),
                    detailCount, username);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            log.error("完成行业初始化失败: user={}, industry={}", username, industryCode, e);
            return ApiResponse.fail(500, "完成行业初始化失败: " + e.getMessage());
        }
    }

    /**
     * 行业解决方案（<b>干跑预演</b>，一行不写）—— 引导页"确认后才执行"的那份方案清单。
     *
     * <p>复用安装引擎的预演：按三层合并（基 → 变体 → 租户）算出"真装会发生什么"，逐条给出
     * 动作与说明（{@code INSERT} / {@code SKIP} / {@code FAILED}）；已在平台级装好的父包会被
     * 标成复用，所以清单里看到的是这家企业<b>真正会得到的东西</b>。
     * 前端据此分「将新增 / 复用平台既有」两段展示。
     */
    @GetMapping("/plan")
    public ApiResponse<Map<String, Object>> plan(@RequestParam("industryCode") String industryCode,
                                                 @RequestParam(value = "devMethodCode", required = false)
                                                 String devMethodCode) {
        if (UserContext.get() == null) {
            return ApiResponse.fail(401, "未登录");
        }
        try {
            PackSource chosen = findIndustryPack(industryCode, trim(devMethodCode));
            if (chosen == null) {
                return ApiResponse.fail(400, "找不到可用的行业包: " + industryCode
                        + "（可能尚未随平台发布，或该包/其基包未启用）");
            }
            User user = userService.findByUsername(UserContext.get());
            if (user == null || user.getTenantOid() == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            List<InstallReport> reports = packEngine.installChain(chosen.getId(), user.getTenantOid(),
                    IndustryPackEngine.noParams(), true);

            List<Map<String, Object>> items = new ArrayList<>();
            List<Map<String, Object>> layers = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int insert = 0;
            int skip = 0;
            int failed = 0;
            for (InstallReport report : reports) {
                // 一个包一层：引擎按"基 → 变体"递归，reports 天然是 platform-base → electronics-core
                // → 行业包 的顺序，引导页据此层层递进地展示（而不是摊平成一坨）。
                // 资源类型 → 中文名：取自该包清单的 resources[].display（名字写在包里，不在前端硬编码）。
                // IBA 是分类安装器从 classifications.json 里"顺带"落地的派生类型（树节点记 CLASSIFICATION、
                // 细分参数记 IBA），清单里没有它的独立条目，故这里兜一个名字 —— 否则管理员会在清单上看到裸码 IBA。
                Map<String, String> displayByType = new LinkedHashMap<>();
                PackSource layerSource = packLoader.find(report.getPackId());
                if (layerSource != null) {
                    for (PackManifest.Resource res : layerSource.getManifest().getResources()) {
                        if (res.getDisplay() != null && !res.getDisplay().isEmpty()) {
                            displayByType.put(res.getType(), res.getDisplay());
                        }
                    }
                }
                displayByType.putIfAbsent("IBA", "分类参数定义");
                // 能力开关也是安装器在运行期产出的一类结果（见 IndustryPackEngine#applyCapabilities），
                // 同样不在清单的 resources[] 里 —— 兜个名字，免得管理员在清单上看到裸码 CAPABILITY。
                displayByType.putIfAbsent("CAPABILITY", "功能能力开关");
                List<Map<String, Object>> layerItems = new ArrayList<>();
                int layerInsert = 0;
                int layerSkip = 0;
                int layerSkipReady = 0;
                int layerSkipPending = 0;
                int layerFailed = 0;
                for (InstallResult result : report.getResults()) {
                    String action = result.getAction() == null ? "" : result.getAction().name();
                    if ("INSERT".equals(action)) {
                        insert++;
                        layerInsert++;
                    } else if ("SKIP".equals(action)) {
                        skip++;
                        layerSkip++;
                        // SKIP 分两类（见 InstallResult#isReady）：已有可用内容 = "已就绪"；
                        // 预期性跳过/前置未就绪 = "跳过"。界面必须分开显示，否则"已就绪 5"这种
                        // 假就绪会把管理员带偏（那 5 条其实只是"预演时还没有可套用布局的类型"）。
                        if (result.isReady()) {
                            layerSkipReady++;
                        } else {
                            layerSkipPending++;
                        }
                    } else if ("FAILED".equals(action)) {
                        // 逐条失败必须上报：只统计 INSERT/SKIP 的话，失败的条目在清单上"消失"，
                        // 管理员以为一切正常（曾把一个行业包的 3 个类型全装丢而界面显示"错误 0"）。
                        failed++;
                        layerFailed++;
                        String failedType = result.getResourceType() == null ? "" : result.getResourceType();
                        errors.add(displayByType.getOrDefault(failedType, failedType) + " "
                                + result.getResourceKey() + " → " + result.getMessage());
                    }
                    String resourceTypeCode = result.getResourceType() == null ? "" : result.getResourceType();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rowKey", report.getPackId() + "#" + items.size());
                    row.put("packId", report.getPackId());
                    row.put("resourceType", resourceTypeCode);
                    // 界面看这个（中文）；resourceType 保留原始码，供排查与对齐包清单
                    row.put("resourceDisplay", displayByType.getOrDefault(resourceTypeCode, resourceTypeCode));
                    row.put("key", result.getResourceKey());
                    row.put("action", action);
                    row.put("ready", result.isReady());
                    row.put("why", result.getMessage());
                    items.add(row);
                    layerItems.add(row);
                }
                // 汇总成"一类资源一行"再给界面：装 IPD 这类包会产出上百条明细（62 个分类参数…），
                // 摊平成一行一条，管理员根本读不完，也看不出"这个包到底给了我哪几类东西"。
                // 逐条明细一条不丢，只是挪进 details 里由前端悬停展开。
                // 只有一条的资源（生命周期状态字典、计量单位、分类树…）沿用安装器原话 ——
                // 那里面有好文案（如"平台已配置 8 条，系统级共享（本企业直接使用，不重复写入）"），不该被汇总口径冲掉。
                Map<String, Map<String, Object>> groupedByType = new LinkedHashMap<>();
                for (Map<String, Object> raw : layerItems) {
                    String type = String.valueOf(raw.get("resourceType"));
                    Map<String, Object> group = groupedByType.get(type);
                    if (group == null) {
                        group = new LinkedHashMap<>();
                        group.put("rowKey", report.getPackId() + "#" + type);
                        group.put("packId", report.getPackId());
                        group.put("resourceType", type);
                        group.put("resourceDisplay", raw.get("resourceDisplay"));
                        String file = null;
                        if (layerSource != null) {
                            for (PackManifest.Resource res : layerSource.getManifest().getResources()) {
                                if (type.equals(res.getType())) {
                                    file = res.getFile();
                                    break;
                                }
                            }
                        }
                        group.put("file", file);
                        group.put("insert", 0);
                        group.put("skip", 0);
                        group.put("skipReady", 0);
                        group.put("skipPending", 0);
                        group.put("failed", 0);
                        group.put("details", new ArrayList<Map<String, Object>>());
                        groupedByType.put(type, group);
                    }
                    String act = String.valueOf(raw.get("action"));
                    String counter = "INSERT".equals(act) ? "insert" : ("FAILED".equals(act) ? "failed" : "skip");
                    group.put(counter, ((Number) group.get(counter)).intValue() + 1);
                    if ("skip".equals(counter)) {
                        // 与逐条明细同一口径：skip 再分"已就绪"与"跳过"（见 InstallResult#isReady）
                        boolean ready = !Boolean.FALSE.equals(raw.get("ready"));
                        String sub = ready ? "skipReady" : "skipPending";
                        group.put(sub, ((Number) group.get(sub)).intValue() + 1);
                    }
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("key", raw.get("key"));
                    detail.put("action", act);
                    detail.put("ready", raw.get("ready"));
                    detail.put("why", raw.get("why"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> detailList = (List<Map<String, Object>>) group.get("details");
                    detailList.add(detail);
                }
                List<Map<String, Object>> layerSummary = new ArrayList<>();
                for (Map<String, Object> group : groupedByType.values()) {
                    int groupInsert = ((Number) group.get("insert")).intValue();
                    int groupSkip = ((Number) group.get("skip")).intValue();
                    int groupSkipReady = ((Number) group.get("skipReady")).intValue();
                    int groupSkipPending = ((Number) group.get("skipPending")).intValue();
                    int groupFailed = ((Number) group.get("failed")).intValue();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> detailList = (List<Map<String, Object>>) group.get("details");
                    group.put("total", groupInsert + groupSkip + groupFailed);
                    group.put("action", groupFailed > 0 ? "FAILED"
                            : (groupInsert > 0 && groupSkip > 0 ? "MIXED"
                            : (groupInsert > 0 ? "INSERT" : "SKIP")));
                    // 组行的就绪标志：只要组里还有"跳过（未就绪）"的条目，就不是全"已就绪"
                    group.put("ready", groupSkipPending == 0);
                    if (detailList.size() == 1) {
                        group.put("why", detailList.get(0).get("why"));
                    } else {
                        StringBuilder sb = new StringBuilder();
                        if (groupInsert > 0) {
                            sb.append("将新增 ").append(groupInsert).append(" 条");
                        }
                        if (groupSkipReady > 0) {
                            sb.append(sb.length() > 0 ? "，" : "").append(groupSkipReady).append(" 条已就绪");
                        }
                        if (groupSkipPending > 0) {
                            sb.append(sb.length() > 0 ? "，" : "").append(groupSkipPending).append(" 条跳过");
                        }
                        if (groupFailed > 0) {
                            sb.append(sb.length() > 0 ? "，" : "").append("失败 ").append(groupFailed).append(" 条");
                        }
                        group.put("why", sb.length() == 0 ? "无可执行内容" : sb.toString());
                    }
                    layerSummary.add(group);
                }
                layerItems = layerSummary;

                Map<String, Object> layer = new LinkedHashMap<>();
                layer.put("packId", report.getPackId());
                layer.put("packName", report.getPackName());
                layer.put("basePackId", report.getBasePackId());
                layer.put("state", report.getInstallState());
                layer.put("reused", "REUSED".equals(report.getInstallState()));
                layer.put("insert", layerInsert);
                layer.put("skip", layerSkip);
                layer.put("skipReady", layerSkipReady);
                layer.put("skipPending", layerSkipPending);
                layer.put("failed", layerFailed);
                layer.put("items", layerItems);
                layers.add(layer);
                errors.addAll(report.getErrors());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("packId", chosen.getId());
            data.put("packName", chosen.getManifest().getName());
            data.put("industryCode", industryCode);
            data.put("devMethodCode", chosen.getManifest().getDevMethod());
            data.put("willInsert", insert);
            data.put("willSkip", skip);
            data.put("willFail", failed);
            data.put("layers", layers);
            data.put("items", items);
            data.put("errors", errors);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            log.error("预演行业初始化失败: industry={}", industryCode, e);
            return ApiResponse.fail(500, "预演失败: " + e.getMessage());
        }
    }

    // ==================== 选项构建 ====================

    /**
     * 全部行业（= 声明了行业与方法论的行业包），<b>含未启用的</b>（引导页要展示"正在建设中"）。
     *
     * <p>启用的排在前面：引导页默认选中第一个选项，不能让默认落在一张点不动的卡上。
     */
    private List<Map<String, Object>> listIndustryOptions() {
        List<Map<String, Object>> enabledFirst = new ArrayList<>();
        List<Map<String, Object>> building = new ArrayList<>();
        for (PackSource source : packLoader.loadAll()) {
            Map<String, Object> item = describeIndustryOption(source);
            if (item == null) {
                continue;
            }
            if (Boolean.FALSE.equals(item.get("enabled"))) {
                building.add(item);
            } else {
                enabledFirst.add(item);
            }
        }
        enabledFirst.addAll(building);
        return enabledFirst;
    }

    /**
     * 按行业（可选方法论）找行业包。
     *
     * <p>只返回<b>启用</b>的包，且整条 {@code basePack} 链都启用 —— 未启用的包不能被租户选，
     * 手工构造请求同样拒之门外（日志里给出原因）。
     */
    private PackSource findIndustryPack(String industryCode, String devMethodCode) {
        for (PackSource source : packLoader.loadAll()) {
            PackManifest manifest = source.getManifest();
            if (!industryCode.equals(manifest.getIndustry())) {
                continue;
            }
            if (devMethodCode == null || devMethodCode.equals(manifest.getDevMethod())) {
                PackManifest blocked = disabledInChain(source);
                if (blocked != null) {
                    log.warn("行业包 {} 未启用（或链上 {} 未启用），不能作为租户的行业选择",
                            source.getId(), blocked.getCode());
                    continue;
                }
                return source;
            }
        }
        return null;
    }

    /**
     * 沿 {@code basePack} 链找第一个被关掉的包；全链启用则返回 null。
     *
     * <p>为什么要看整条链：变体包（如 smart-hardware-ipd）的内容建立在基包（platform-base /
     * electronics-core）之上 —— 基包被关掉还允许选变体，装出来就是个"缺一半"的解决方案，
     * 而且清单上还写着成功。宁可不让选。
     */
    private PackManifest disabledInChain(PackSource source) {
        PackSource current = source;
        for (int depth = 0; current != null && depth < 8; depth++) {
            PackManifest manifest = current.getManifest();
            if (manifest == null) {
                return null;
            }
            if (!manifest.isEnabled()) {
                return manifest;
            }
            String base = manifest.getBasePackId();
            current = base == null ? null : packLoader.find(base);
        }
        return null;
    }

    /**
     * 把一个包描述成"行业 → 方法论"选项；不是行业包（缺 industry 或 devMethod）时返回 null。
     * 阶段链从包内 STAGE 资源里读 —— 让管理员在选之前就看到这条方法论长什么样。
     *
     * <p>未启用的包（或链上有未启用的包）<b>不进选项</b>：引导页只列真能选的。
     */
    private Map<String, Object> describeIndustryOption(PackSource source) {
        PackManifest manifest = source.getManifest();
        if (manifest.getIndustry() == null || manifest.getDevMethod() == null) {
            return null;
        }
        List<String> stages = readStageNames(source);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("industryCode", manifest.getIndustry());
        item.put("devMethod", manifest.getDevMethod());
        item.put("packId", source.getId());
        item.put("packName", manifest.getName());
        item.put("targetScale", manifest.getTargetScale());
        item.put("controlLevel", manifest.getControlLevel());
        // 细分行业 / 主要产品形态：由行业包自己声明（[{code,name}]），引导页下拉直接用
        item.put("segments", manifest.getSegments());
        item.put("productForms", manifest.getProductForms());
        // 本包提供的能力开关（包内 capabilities.json 的 capabilities[]，形状 [{code,name,default,note?}]）：
        // 选行业时就要让管理员看见"这个包给我哪些能力"，而不是装完才发现多出一片菜单。
        // default=false 表示"登记了但当前不启用"（例如依赖尚未实现的模块）——界面据此标灰并显示 note，
        // 不假装它已生效（包与平台版本解耦的关键：装得上，但界面上说清楚哪些还没来）。
        Object capNode = source.getCapabilities().get("capabilities");
        List<?> capabilities = capNode instanceof List ? (List<?>) capNode : List.of();
        item.put("capabilities", capabilities);
        item.put("capabilityCount", capabilities.size());
        item.put("capabilityEnabledCount", capabilities.stream()
                .filter(c -> c instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) c).get("default")))
                .count());
        item.put("stages", stages);
        item.put("stageCount", stages.size());
        item.put("summary", stages.isEmpty()
                ? manifest.getDevMethod() + " 研发方法论"
                : stages.size() + " 个阶段：" + String.join(" → ", stages));
        // 未启用的包<b>照样列出来</b>：平台已规划但尚未交付的行业，该让租户管理员看得见
        // （界面上标"正在建设中"），只是不许选中 —— 藏起来反而像"平台没这个行业"。
        // 链上有包被关掉（如变体的基包）同样算"建设中"：缺基的解决方案不能给租户选。
        // 注意：能不能选由这里的 enabled 决定；真正的闸门还在 findIndustryPack()（手工构造请求也拦）。
        PackManifest disabled = disabledInChain(source);
        item.put("enabled", disabled == null);
        if (disabled != null) {
            item.put("disabledReason", disabled.getCode().equals(manifest.getCode())
                    ? "该行业包尚未启用"
                    : "其基包 " + disabled.getCode() + " 尚未启用");
        }
        return item;
    }

    /** 读包内 STAGE 资源声明的阶段名（按文件里的顺序） */
    private List<String> readStageNames(PackSource source) {
        List<String> names = new ArrayList<>();
        for (PackManifest.Resource resource : source.getManifest().getResources()) {
            if (!"STAGE".equalsIgnoreCase(resource.getType()) || resource.getFile() == null) {
                continue;
            }
            try {
                Map<String, Object> json = source.readJsonMap(objectMapper, resource.getFile());
                Object industries = json.get("industries");
                if (!(industries instanceof List)) {
                    continue;
                }
                for (Object element : (List<?>) industries) {
                    if (!(element instanceof Map)) {
                        continue;
                    }
                    Object stageList = ((Map<?, ?>) element).get("stages");
                    if (!(stageList instanceof List)) {
                        continue;
                    }
                    for (Object stage : (List<?>) stageList) {
                        if (stage instanceof Map) {
                            Object name = ((Map<?, ?>) stage).get("name");
                            if (name != null) {
                                names.add(String.valueOf(name));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("读取包 {} 的阶段清单失败: {}", source.getId(), e.getMessage());
            }
        }
        return names;
    }

    /** 是否为本租户的租户管理员（直接用 JDBC 绕过 MyBatis 拦截器对 JOIN 的改写） */
    private boolean isTenantAdmin(String userOid) {
        try {
            // ★ 角色按租户隔离后 role.code 各租户各一份，必须限定"角色与成员同租户"，否则会命中别家的副本
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ck_role_member rm "
                            + "INNER JOIN ck_role r ON rm.role_oid = r.oid "
                            + "WHERE rm.user_oid = ? AND r.code = ? AND r.tenant_oid = rm.tenant_oid",
                    Integer.class, userOid, TENANT_ADMIN_ROLE);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("租户管理员校验失败: userOid={}, {}", userOid, e.getMessage());
            return false;
        }
    }

    private static String trim(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
