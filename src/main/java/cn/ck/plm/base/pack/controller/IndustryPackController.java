/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.controller;

import cn.ck.plm.base.pack.engine.IndustryPackEngine;
import cn.ck.plm.base.pack.install.InstallReport;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业包管理接口 —— 列出可用包、校验、预演（dry-run）、安装。
 *
 * <h3>这一层是"薄"的</h3>
 * 全部逻辑在 {@link IndustryPackEngine}：控制器只做三件事 —— 取当前租户、把参数原样传下去、
 * 把报告包成 {@link ApiResponse}。行业包的所有语义（依赖、幂等、预演、参数保留）都在引擎里，
 * 换一个入口（引导页 / 平台控制台 / 命令行）不需要改引擎。
 *
 * <h3>租户口径</h3>
 * 平台级包（platform-base / electronics-core）由启动引导装到平台租户；
 * 行业变体包（smart-hardware-ipd / electronics-hitech）在租户引导式初始化时装到<b>该租户</b>。
 * 接口默认按<b>当前租户</b>落归属，无上下文时退化为平台租户（与初始化器的写法一致）。
 */
@RestController
@RequestMapping("/api/industry-packs")
public class IndustryPackController {

    private final IndustryPackLoader loader;
    private final IndustryPackEngine engine;

    public IndustryPackController(IndustryPackLoader loader, IndustryPackEngine engine) {
        this.loader = loader;
        this.engine = engine;
    }

    /** 当前租户（无上下文 → 平台租户） */
    private String currentTenant() {
        String tenant = TenantContext.getOrNull();
        return tenant == null || tenant.trim().isEmpty() ? TenantContext.PLATFORM_TENANT_OID : tenant;
    }

    /**
     * 可用包列表（来自包目录，非数据库）—— 引导页"选行业 / 选方法论"的候选来源。
     */
    @GetMapping("/available")
    @Operation(summary = "可用行业包列表")
    public ApiResponse<List<Map<String, Object>>> available() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PackSource source : loader.loadAll()) {
            result.add(summaryOf(source));
        }
        return ApiResponse.ok(result);
    }

    /**
     * 已安装的包（含平台级 + 当前租户级）。
     */
    @GetMapping("/installed")
    @Operation(summary = "已安装的行业包")
    public ApiResponse<List<Map<String, Object>>> installed() {
        return ApiResponse.ok(engine.listInstalled(currentTenant()));
    }

    /**
     * 校验全部包：平台版本区间 / 父包是否存在 / 资源文件是否齐 —— 安装前的"体检"。
     */
    @GetMapping("/validate")
    @Operation(summary = "校验全部行业包")
    public ApiResponse<Map<String, Object>> validate() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platformVersion", engine.getPlatformVersion());
        result.put("packs", engine.validateAll());
        return ApiResponse.ok(result);
    }

    /**
     * 包详情（清单 + 能力开关）—— 引导页"内容预演"第 2 步的数据源。
     */
    @GetMapping("/{code}/{version}")
    @Operation(summary = "行业包详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String code, @PathVariable String version) {
        PackSource source = loader.find(code + "@" + version);
        if (source == null) {
            return ApiResponse.fail(404, "包不存在: " + code + "@" + version);
        }
        Map<String, Object> result = summaryOf(source);
        result.put("manifest", source.getManifest());
        result.put("capabilities", source.getCapabilities());
        return ApiResponse.ok(result);
    }

    /**
     * <b>包内容详情</b> —— 平台管理员"查看行业与内嵌方法论内容"的主数据源。
     *
     * <p>返回：清单元数据 + 能力开关 + 企业画像题 + 可调参数（含按规模建议值）+ 内容亮点 +
     * 装完后的待办 + 逐资源条目预览（阶段 / 交付物 / 角色 … P1 抽取完成后即有内容）。
     * 只读，不写库。
     */
    @GetMapping("/{code}/{version}/content")
    @Operation(summary = "行业包内容详情（平台管理员查看方法论内容）")
    public ApiResponse<Map<String, Object>> content(@PathVariable String code, @PathVariable String version) {
        PackSource source = loader.find(code + "@" + version);
        if (source == null) {
            return ApiResponse.fail(404, "包不存在: " + code + "@" + version);
        }
        return ApiResponse.ok(engine.contentDetail(source, currentTenant()));
    }

    /**
     * 预演（dry-run）：回答"真装会得到什么"，<b>一行都不写库</b>。
     * 引导页第 3 步可反复调用（用户来回改参数时不会产生任何副作用）。
     */
    @GetMapping("/{code}/{version}/plan")
    @Operation(summary = "安装预演（不写库）")
    public ApiResponse<List<InstallReport>> plan(@PathVariable String code, @PathVariable String version) {
        List<InstallReport> reports = engine.installChain(code + "@" + version, currentTenant(),
                IndustryPackEngine.noParams(), true);
        return ApiResponse.ok(reports);
    }

    /**
     * 安装（幂等）。请求体可选：{@code { "params": { "genPartThreshold.minModelCount": 4 } }}。
     *
     * @return 按安装顺序的报告（父包在前，目标包在最后）
     */
    @PostMapping("/{code}/{version}/install")
    @Operation(summary = "安装行业包（幂等）")
    @SuppressWarnings("unchecked")
    public ApiResponse<List<InstallReport>> install(@PathVariable String code, @PathVariable String version,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body == null ? null : (Map<String, Object>) body.get("params");
        List<InstallReport> reports = engine.installChain(code + "@" + version, currentTenant(),
                params == null ? IndustryPackEngine.noParams() : params, false);
        InstallReport last = reports.isEmpty() ? null : reports.get(reports.size() - 1);
        if (last != null && !last.getErrors().isEmpty()) {
            return ApiResponse.fail(400, String.join("; ", last.getErrors()));
        }
        return ApiResponse.ok(reports);
    }

    // ==================== 内部 ====================

    private Map<String, Object> summaryOf(PackSource source) {
        PackManifest manifest = source.getManifest();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("packId", source.getId());
        row.put("code", manifest.getCode());
        row.put("version", manifest.getVersion());
        row.put("name", manifest.getName());
        row.put("basePack", manifest.getBasePack());
        row.put("engineRange", manifest.getEngineRange());
        row.put("industry", manifest.getIndustry());
        row.put("devMethod", manifest.getDevMethod());
        row.put("targetScale", manifest.getTargetScale());
        row.put("controlLevel", manifest.getControlLevel());
        row.put("license", manifest.getLicense());
        row.put("primaryScopeType", manifest.getPrimaryScopeType());
        row.put("selectableInOnboarding", manifest.isSelectableInOnboarding());
        // 启用标记：未启用的包不进租户引导的可选项，界面据此标"未启用"（缺省启用）
        row.put("enabled", manifest.isEnabled());
        row.put("resourceCounts", manifest.resourceCounts());
        row.put("previewHints", manifest.getPreviewHints());
        row.put("nextSteps", manifest.getNextSteps());
        row.put("capabilityCount", source.getCapabilities().isEmpty()
                ? 0 : ((List<?>) source.getCapabilities().getOrDefault("capabilities", List.of())).size());
        return row;
    }
}
