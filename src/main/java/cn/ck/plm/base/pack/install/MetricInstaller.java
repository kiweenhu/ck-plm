/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.MetricTemplate;
import cn.ck.plm.base.mapper.MetricTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 指标（驾驶舱）模板安装器（{@code METRIC}）—— 读包内 {@code metrics.json}。
 *
 * <h3>与前面六类最大的不同：清单形状</h3>
 * 其它资源都是 {@code items[]} 一个数组；指标按<b>分层</b>写：
 * {@code businessLayer[]}（研发总监看：成本 / 器件 / 问题）与
 * {@code executiveLayer[]}（老板看：上市 / 复用 / 质量成本）。
 * 本安装器认这两种数组（并兼容 {@code items[]} + 条目自带 {@code layer} 的写法），
 * 把数组名落成 {@code layer} 字段 —— 分层是这套指标的口径之一，不能丢。
 *
 * <h3>level 是硬约束，不是装饰</h3>
 * {@code A / B / C} 三级<b>数据就绪度</b>，文档第 16 节原话：
 * "C 级指标第一版显示『暂未评估』，<b>禁止编造数值</b> —— 一个编出来的数字被客户当场问穿，
 * 整个健康度报告的可信度就归零了。"
 * 故越界即如实记 FAILED；C 级条目在安装报告里也会明写"第一版显示暂未评估"，
 * 免得日后有人把它当成一个待补的普通指标。
 *
 * <h3>幂等</h3>
 * 键 {@code code + 目标租户}：已存在即 SKIP（企业裁剪不被覆盖），安装器永不 UPDATE。
 */
@Component
public class MetricInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(MetricInstaller.class);

    /** 分层（与包内 metrics.json 的数组名一一对应） */
    private static final String LAYER_BUSINESS = "businessLayer";
    private static final String LAYER_EXECUTIVE = "executiveLayer";

    /** 数据就绪度 */
    private static final Set<String> LEVELS = new LinkedHashSet<>(List.of("A", "B", "C"));

    private final MetricTemplateMapper mapper;

    public MetricInstaller(MetricTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String resourceType() {
        return "METRIC";
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

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);

        // 分层数组优先；兼容 items[] + 条目自带 layer 的写法
        List<Map<?, ?>> rows = new ArrayList<>();
        collect(json.get(LAYER_BUSINESS), LAYER_BUSINESS, rows);
        collect(json.get(LAYER_EXECUTIVE), LAYER_EXECUTIVE, rows);
        if (rows.isEmpty()) {
            collect(json.get("items"), null, rows);
        }
        if (rows.isEmpty()) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "清单里没有 businessLayer / executiveLayer / items 段（本包不含指标）"));
            return results;
        }
        log.debug("指标安装：industry={}（空=通用，基础包共用指标即如此），条目={} 条",
                industry, rows.size());

        int order = 0;
        for (Map<?, ?> row : rows) {
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            order++;
            try {
                String layer = text(row.get("layer"));
                if (layer == null) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "缺 layer：指标必须归入 " + LAYER_BUSINESS + " 或 " + LAYER_EXECUTIVE));
                    continue;
                }
                String level = text(row.get("level"));
                if (level == null || !LEVELS.contains(level.toUpperCase())) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "level 取值非法（只能是 " + LEVELS + "）：" + row.get("level")));
                    continue;
                }
                level = level.toUpperCase();

                if (mapper.existsByCode(code, tenant, tenant) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code,
                            "指标已存在（企业裁剪值不被覆盖）"));
                    continue;
                }
                MetricTemplate template = build(row, industry, tenant, order, layer, level);
                String layerLabel = LAYER_EXECUTIVE.equals(layer) ? "经营层" : "业务层";
                String sourceLabel = template.getSource() == null ? "来源未标注" : "来源 " + template.getSource();
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增指标「" + template.getName() + "」（" + layerLabel
                                    + " / 就绪度 " + level + "，" + sourceLabel + "）"));
                    continue;
                }
                mapper.insert(template);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("指标：" + template.getName() + "（" + layerLabel + " / 就绪度 " + level
                                + "，" + sourceLabel + readinessNote(level) + "）")
                        .on("ck_metric_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("指标安装完成: {} 条明细", results.size());
        return results;
    }

    /** 把分层数组摊平成待装行，并把数组名写进条目的 layer（条目自带 layer 时不覆盖） */
    private void collect(Object node, String layerFromArray, List<Map<?, ?>> out) {
        if (!(node instanceof List)) {
            return;
        }
        for (Object element : (List<?>) node) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            if (layerFromArray != null && text(row.get("layer")) == null) {
                // 用一个可变副本：不改动源 JSON（PackSource 可能被复用）
                Map<Object, Object> copy = new java.util.LinkedHashMap<>(row);
                copy.put("layer", layerFromArray);
                out.add(copy);
            } else {
                out.add(row);
            }
        }
    }

    /**
     * C 级指标在报告里就把话说死：第一版显示"暂未评估"（文档硬规则，禁止编造数值）。
     */
    private String readinessNote(String level) {
        return "C".equals(level) ? "；C 级第一版显示「暂未评估」，禁止编造数值" : "";
    }

    /**
     * 行业/方法论：优先清单自己声明的 {@code industry}，其次包清单的 {@code devMethod}，都没有则留空
     * （空 = 通用。基础包 electronics-core 不声明方法论，其共用指标就落成通用条目 —— 这是有意的语义）。
     */
    private String resolveIndustry(InstallContext context, Map<String, Object> json) {
        String fromFile = text(json.get("industry"));
        if (fromFile != null) {
            return fromFile;
        }
        return text(context.getSource().getManifest().getDevMethod());
    }

    private MetricTemplate build(Map<?, ?> row, String industry, String tenant, int order,
                                 String layer, String level) {
        MetricTemplate template = new MetricTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setLayer(layer);
        template.setLevel(level);
        template.setSource(text(row.get("source")));
        // 包内字段名是 note；也认 industryNote（便于将来七类模板统一命名）
        String note = text(row.get("industryNote"));
        if (note == null) {
            note = text(row.get("note"));
        }
        template.setIndustryNote(note);
        template.setDisabled(false);
        template.setSortOrder(intOr(row.get("sortOrder"), order));
        template.setTenantOid(tenant);
        // 装包是系统动作（与引擎落能力账本同口径）；后续人工修改由审计拦截器改写
        template.setCreator("system");
        template.setUpdater("system");
        return template;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOr(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
