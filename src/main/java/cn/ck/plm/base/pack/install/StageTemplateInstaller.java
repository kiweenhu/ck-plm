/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.StageTemplate;
import cn.ck.plm.base.mapper.StageTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 阶段模板安装器（{@code STAGE}）—— 读包内 {@code stages.json} 的 {@code industries[].stages[]}。
 *
 * <p><b>数据来源变迁</b>：原先 23 个阶段模板写在 {@code StageTemplateServiceImpl} 的静态 map
 * （{@code DEFAULT_STAGES_BY_INDUSTRY}）里，现拆到两个包：
 * <ul>
 *   <li>{@code platform-base} —— TRADITIONAL / MILITARY / AUTOMOTIVE（平台级通用方法论）；</li>
 *   <li>{@code smart-hardware-ipd} —— IPD 六阶段（行业方法论，属电子高科/智能硬件的"方法"本体）。</li>
 * </ul>
 * 之所以把 IPD 放进行业包：换行业=换包，六阶段连同将来的阶段门/交付物一起走，不必改代码。
 *
 * <p><b>字段映射</b>：{@code defaultFolders} / {@code managedObjectTypes} 在库里是 JSON 字符串，
 * 包里写成真数组（可读、可审）→ 安装器序列化成与原先<b>逐字一致</b>的字符串（无空格）。
 * 包里的 {@code subMilestones} / {@code gate} 是行业增量，平台侧无对应列，本版先不落库（P3 建列后启用）。
 *
 * <p><b>幂等键</b>：{@code code + tenant}（沿用 {@code StageTemplateMapper.existsByCode} 的判据）。
 */
@Component
public class StageTemplateInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(StageTemplateInstaller.class);

    private final StageTemplateMapper mapper;

    public StageTemplateInstaller(StageTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String resourceType() {
        return "STAGE";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }

        Object industries = json.get("industries");
        if (!(industries instanceof List)) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "模板里没有 industries 段（本包不含阶段模板）"));
            return results;
        }

        String tenant = context.getTenantOid();
        for (Object element : (List<?>) industries) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> industryRow = (Map<?, ?>) element;
            String industry = text(industryRow.get("industry"));
            Object stages = industryRow.get("stages");
            if (industry == null || !(stages instanceof List)) {
                continue;
            }
            for (Object stageElement : (List<?>) stages) {
                if (!(stageElement instanceof Map)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) stageElement;
                String code = text(row.get("code"));
                if (code == null) {
                    continue;
                }
                try {
                    if (mapper.existsByCode(code, tenant, tenant) > 0) {
                        results.add(InstallResult.skipped(resourceType(), industry + "/" + code, "阶段模板已存在"));
                        continue;
                    }
                    StageTemplate template = build(row, industry, tenant);
                    if (context.isDryRun()) {
                        results.add(InstallResult.inserted(resourceType(), industry + "/" + code)
                                .why("预演：将新增阶段「" + template.getName() + "」（" + industry + "）"));
                        continue;
                    }
                    mapper.insert(template);
                    results.add(InstallResult.inserted(resourceType(), industry + "/" + code)
                            .why("阶段模板：" + template.getName() + "（" + industry + "，"
                                    + folderCount(template) + " 个默认文件夹）")
                            .on("ck_stage_template", template.getOid()));
                } catch (Exception e) {
                    results.add(InstallResult.failed(resourceType(), industry + "/" + code,
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }
        }
        log.debug("阶段模板安装完成: {} 条明细", results.size());
        return results;
    }

    /** 按包里的数组构造实体 —— 与原先静态 map 的 {@code add(...)} 填充器逐字段一致 */
    private StageTemplate build(Map<?, ?> row, String industry, String tenant) throws Exception {
        StageTemplate template = new StageTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIcon(text(row.get("icon")));
        template.setColor(text(row.get("color")));
        template.setSortOrder(intOr(row.get("sortOrder"), 0));
        template.setIndustry(industry);
        template.setTenantOid(tenant);
        // 库里是 JSON 字符串，包里是可读数组 → 序列化回来（Jackson 默认无空格，与原先字面量一致）
        template.setDefaultFolders(toJsonArray(row.get("defaultFolders")));
        template.setManagedObjectTypes(toJsonArray(row.get("managedObjectTypes")));
        return template;
    }

    private String toJsonArray(Object value) throws Exception {
        if (value instanceof List) {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .writeValueAsString(value);
        }
        return value == null ? null : String.valueOf(value);
    }

    private int folderCount(StageTemplate template) {
        String folders = template.getDefaultFolders();
        if (folders == null || folders.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < folders.length(); i++) {
            if (folders.charAt(i) == '"') {
                count++;
            }
        }
        return count / 2;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOr(Object value, int defaultValue) {
        if (value instanceof java.lang.Number) {
            return ((java.lang.Number) value).intValue();
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
