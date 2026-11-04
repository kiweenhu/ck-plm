/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.DashboardTemplate;
import cn.ck.plm.base.mapper.DashboardTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 驾驶舱（布局）模板安装器（{@code DASHBOARD}）—— 读包内 {@code dashboards.json}。
 *
 * <h3>与前面七类最大的不同：两个"包级键"</h3>
 * 清单是 {@code scopes[]}（视角）+ 两个包级键，安装器负责把它们摊平到行上：
 * <ol>
 *   <li>{@code primaryScopeType}（主视角）→ 命中那条的 {@code primary_scope = true}。
 *       <b>必须恰有一条命中</b>：命不中说明包内容有缺陷（主视角指向了不存在的视角），记 FAILED；</li>
 *   <li>{@code generatePolicy}（{@code {视角 → 生成节奏}} 映射）→ 逐行摊到 {@code generate_policy}。
 *       映射里的每个 key 都必须是一个真实存在的视角 —— 悬空 key 同样记 FAILED
 *       （与阶段门/评审的"不允许悬空引用"同一纪律）。</li>
 * </ol>
 *
 * <h3>widgets 只校验非空</h3>
 * 分组件码里有一部分<b>就是指标码</b>（{@code COST_ACHIEVE} 在 metrics.json 里有）；
 * 其余（{@code MODEL_RANKING} / {@code STAGE_READINESS}…）目前<b>没有定义表</b> ——
 * 故这里只校验"每个视角至少有一个分组件"，<b>不假装能校验其存在性</b>。
 *
 * <h3>幂等</h3>
 * 键 {@code code（视角类型）+ 目标租户}：已存在即 SKIP（企业裁剪不被覆盖），安装器永不 UPDATE。
 */
@Component
public class DashboardInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(DashboardInstaller.class);

    private final DashboardTemplateMapper mapper;

    public DashboardInstaller(DashboardTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String resourceType() {
        return "DASHBOARD";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取清单文件失败: " + e.getMessage()));
            return results;
        }

        Object scopesNode = json.get("scopes");
        if (!(scopesNode instanceof List) || ((List<?>) scopesNode).isEmpty()) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "清单里没有 scopes 段（本包不含驾驶舱模板）"));
            return results;
        }

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);
        String primaryScopeType = text(json.get("primaryScopeType"));
        Map<String, Object> policyMap = asMap(json.get("generatePolicy"));

        // 摊平一次，供两处悬空校验使用
        List<Map<?, ?>> scopes = new ArrayList<>();
        for (Object element : (List<?>) scopesNode) {
            if (element instanceof Map) {
                scopes.add((Map<?, ?>) element);
            }
        }
        List<String> scopeTypes = new ArrayList<>();
        for (Map<?, ?> scope : scopes) {
            String type = text(scope.get("type"));
            if (type != null) {
                scopeTypes.add(type);
            }
        }

        // ① 主视角必须命中一个真实视角（包内容缺陷 → FAILED，但不阻断其余行安装）
        boolean primaryMatched = primaryScopeType != null && scopeTypes.contains(primaryScopeType);
        if (primaryScopeType == null) {
            results.add(InstallResult.failed(resourceType(), "primaryScopeType",
                    "缺 primaryScopeType：没有主视角，驾驶舱不知道该以哪一层为工作面"));
        } else if (!primaryMatched) {
            results.add(InstallResult.failed(resourceType(), "primaryScopeType",
                    "主视角 '" + primaryScopeType + "' 不在 scopes 里（可选：" + scopeTypes + "）"));
        }

        // ② generatePolicy 的 key 不许悬空
        for (String key : policyMap.keySet()) {
            if (!scopeTypes.contains(key)) {
                results.add(InstallResult.failed(resourceType(), "generatePolicy." + key,
                        "生成策略挂在了一个不存在的视角上（可选：" + scopeTypes + "）"));
            }
        }

        log.debug("驾驶舱安装：industry={}（空=通用），视角={} 个，主视角={}",
                industry, scopes.size(), primaryScopeType);

        int order = 0;
        for (Map<?, ?> scope : scopes) {
            String type = text(scope.get("type"));
            if (type == null) {
                continue;
            }
            order++;
            try {
                List<String> widgets = asStringList(scope.get("widgets"));
                if (widgets.isEmpty()) {
                    results.add(InstallResult.failed(resourceType(), type,
                            "该视角没有分组件（widgets 为空）—— 空驾驶舱没有意义"));
                    continue;
                }
                if (mapper.existsByCode(type, tenant, tenant) > 0) {
                    results.add(InstallResult.skipped(resourceType(), type,
                            "驾驶舱布局已存在（企业裁剪值不被覆盖）"));
                    continue;
                }
                String policy = text(policyMap.get(type));
                DashboardTemplate template = build(scope, industry, tenant, order, type,
                        policy, primaryScopeType != null && primaryScopeType.equals(type));
                String audience = template.getAudience() == null ? "受众未标注" : "受众 " + template.getAudience();
                String pageLabel = template.getPageCount() == null ? "" : "，" + template.getPageCount() + " 页";
                String policyLabel = template.getGeneratePolicy() == null
                        ? "，生成策略未标注" : "，生成节奏 " + template.getGeneratePolicy();
                String primaryLabel = Boolean.TRUE.equals(template.getPrimaryScope()) ? "，★主视角" : "";
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), type)
                            .why("预演：将新增驾驶舱「" + template.getName() + "」（" + type + "，"
                                    + audience + pageLabel + "，" + widgets.size() + " 个分组件"
                                    + policyLabel + primaryLabel + "）"));
                    continue;
                }
                mapper.insert(template);
                results.add(InstallResult.inserted(resourceType(), type)
                        .why("驾驶舱模板：" + template.getName() + "（" + type + "，" + audience + pageLabel
                                + "，" + widgets.size() + " 个分组件"
                                + (widgets.size() <= 4 ? "：" + String.join("、", widgets) : "")
                                + policyLabel + primaryLabel + "）")
                        .on("ck_dashboard_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), type,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("驾驶舱安装完成: {} 条明细", results.size());
        return results;
    }

    private DashboardTemplate build(Map<?, ?> scope, String industry, String tenant, int order,
                                    String type, String policy, boolean primary) throws Exception {
        DashboardTemplate template = new DashboardTemplate(type, text(scope.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setAudience(text(scope.get("audience")));
        template.setPageCount(intOrNull(scope.get("pageCount")));
        template.setWidgets(toJson(asStringList(scope.get("widgets"))));
        template.setGeneratePolicy(policy);
        template.setPrimaryScope(primary);
        template.setDescription(text(scope.get("description")));
        template.setDisabled(false);
        template.setSortOrder(intOr(scope.get("sortOrder"), order));
        template.setTenantOid(tenant);
        // 装包是系统动作（与引擎落能力账本同口径）；后续人工修改由审计拦截器改写
        template.setCreator("system");
        template.setUpdater("system");
        return template;
    }

    /**
     * 行业/方法论：优先清单自己声明的 {@code industry}，其次包清单的 {@code devMethod}，都没有则留空
     * （空 = 通用，任何方法论都适用）。不猜、不硬编码。
     */
    private String resolveIndustry(InstallContext context, Map<String, Object> json) {
        String fromFile = text(json.get("industry"));
        if (fromFile != null) {
            return fromFile;
        }
        return text(context.getSource().getManifest().getDevMethod());
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() != null) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return map;
    }

    private static List<String> asStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List) {
            for (Object element : (List<?>) value) {
                String s = text(element);
                if (s != null) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private String toJson(List<String> values) throws Exception {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(values);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int intOr(Object value, int defaultValue) {
        Integer parsed = intOrNull(value);
        return parsed == null ? defaultValue : parsed;
    }
}
