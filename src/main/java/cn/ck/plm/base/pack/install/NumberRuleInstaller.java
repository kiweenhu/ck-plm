/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.Number;
import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.base.mapper.NumberMapper;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编码规则安装器（{@code NUMBER_RULE}）—— 读包内 {@code numberRules.json} 的 {@code items[]}。
 *
 * <p><b>数据来源变迁</b>：原先写在 {@code NumberRuleInitializer}（代码即数据），现随
 * {@code electronics-core} 包播种。段构造与原实现<b>逐字对齐</b>（CONST/SEPARATOR 用三段构造器、
 * YEAR/MONTH/DAY 用带 format 的四段构造器、SERIAL 用 digits+start 的四段构造器），
 * 以保证"抽取前后落库结果完全一致"。
 *
 * <p><b>幂等粒度</b>：规则级按 {@code ck_number.code} 判重；规则已存在时连段也不碰
 * —— 管理员改过段配置（位数、起始值）不该被包的默认值覆盖。
 */
@Component
public class NumberRuleInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(NumberRuleInstaller.class);

    private final NumberMapper numberMapper;
    private final NumberSegmentMapper numberSegmentMapper;

    public NumberRuleInstaller(NumberMapper numberMapper, NumberSegmentMapper numberSegmentMapper) {
        this.numberMapper = numberMapper;
        this.numberSegmentMapper = numberSegmentMapper;
    }

    @Override
    public String resourceType() {
        return "NUMBER_RULE";
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

        Object items = json.get("items");
        if (!(items instanceof List)) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "模板里没有 items 段（本包不含编码规则）"));
            return results;
        }

        for (Object element : (List<?>) items) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            String name = text(row.get("name"));
            if (code == null) {
                continue;
            }
            try {
                List<NumberSegment> segments = buildSegments(row.get("segments"));
                if (numberMapper.existsByCode(code) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code, "编码规则已存在"));
                    continue;
                }
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增编码规则「" + name + "」及其 " + segments.size() + " 个段"));
                    continue;
                }
                Number number = new Number(code, name);
                number.setOid(UUID.randomUUID().toString());
                number.setEnabled(true);
                number.setDescription(name + "（系统预置）");
                number.setTenantOid(context.getTenantOid());
                numberMapper.insert(number);

                int inserted = 0;
                for (NumberSegment segment : segments) {
                    segment.setOid(UUID.randomUUID().toString());
                    segment.setRuleCode(code);
                    numberSegmentMapper.insert(segment);
                    inserted++;
                }
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("编码规则：" + name + "（" + inserted + " 段）")
                        .on("ck_number", number.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("编码规则安装完成: {} 条明细", results.size());
        return results;
    }

    /**
     * 段构造 —— 与 {@code NumberRuleInitializer.buildDefaultNumberSegments} 的三个构造器一一对应。
     * 段类型：CONST / SEPARATOR（固定值）· YEAR / MONTH / DAY（日期格式）· SERIAL（位数 + 起始值）。
     */
    private List<NumberSegment> buildSegments(Object raw) {
        List<NumberSegment> segments = new ArrayList<>();
        if (!(raw instanceof List)) {
            return segments;
        }
        for (Object element : (List<?>) raw) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String type = text(row.get("type"));
            Integer sortOrder = intOrNull(row.get("sortOrder"));
            if (type == null || sortOrder == null) {
                continue;
            }
            switch (type.toUpperCase()) {
                case "CONST":
                case "SEPARATOR":
                    segments.add(new NumberSegment(type.toUpperCase(), text(row.get("value")), sortOrder));
                    break;
                case "YEAR":
                case "MONTH":
                case "DAY":
                    segments.add(new NumberSegment(type.toUpperCase(), text(row.get("format")), null, sortOrder));
                    break;
                case "SERIAL":
                    segments.add(new NumberSegment("SERIAL",
                            intOr(row.get("digits"), 1), intOr(row.get("start"), 1), sortOrder));
                    break;
                default:
                    break;
            }
        }
        return segments;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer intOrNull(Object value) {
        // 注意：本文件 import 了 cn.ck.plm.base.entity.Number（编码规则实体），
        // 它遮住了 java.lang.Number —— 这里必须写全限定名，否则编译不过。
        if (value instanceof java.lang.Number) {
            return ((java.lang.Number) value).intValue();
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
        Integer v = intOrNull(value);
        return v == null ? defaultValue : v;
    }
}
