/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.Number;
import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.base.mapper.NumberMapper;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 应用启动时初始化「编码规则」（{@code ck_number} + 编码段 {@code ck_number_segment}）——
 * 平台预置，写入<b>平台租户</b>，各租户共用（与版本规则 {@code ck_version_rule} 的种子口径一致）。
 *
 * <h3>预置规则与编号示例</h3>
 * <pre>
 * PRODUCT_LINE      PL-001                    产品系列
 * PRODUCT_MODEL     PM-2026-001               产品型号
 * DOC_NUMBER        DOC-202601-0001           文档
 * PART_NUMBER       PART-202601-0001          部件
 * FUNCTIONAL-NUM    FUNC-20260101-00000001    功能架构
 * ECAD_PROJECT-NUM  ECAD-202601-0001          电子设计项目
 * </pre>
 *
 * <h3>为什么单独成类</h3>
 * 这些规则原先长在 {@code TypeDefinitionInitializer} 里：类型注册与「平台基础规则」是两件事，
 * 混在一起时读类型逻辑会撞见编号段配置、改编号规则又要动类型初始化器。现在按
 * 「一个能力一个初始化器」拆开（与 {@link VersionRuleInitializer} / {@link UnitInitializer}
 * / {@link LifecycleStatusInitializer} 同口径）。
 *
 * <h3>顺序</h3>
 * {@code @Order(1)}：与 {@code VersionRuleInitializer} 同级，且<b>必须早于</b>
 * {@code TypeDefinitionInitializer}(2) —— 类型注册时要按 code 绑定编码规则
 * （{@code ck_type_number_rule_link}），规则得先存在。
 *
 * <h3>幂等性</h3>
 * 按 code 逐条判断，缺哪条补哪条；已存在的（含管理员在界面上改过段配置的）<b>不覆盖</b>。
 *
 * <h3>租户</h3>
 * 启动期没有请求上下文，{@code TenantContext.get()} 会退化成「默认租户」，所以这里显式切到
 * 平台租户再写、结束时还原（{@code ck_number.tenant_oid} 由实体显式赋值，不依赖拦截器）。
 */
@Component
@Order(1)
public class NumberRuleInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NumberRuleInitializer.class);

    /** 默认编码规则：code → 名称（LinkedHashMap 保证顺序稳定、日志可读） */
    private static final Map<String, String> DEFAULT_RULES = new LinkedHashMap<>();

    static {
        DEFAULT_RULES.put("PRODUCT_LINE", "产品系列编码");
        DEFAULT_RULES.put("PRODUCT_MODEL", "产品型号编码");
        DEFAULT_RULES.put("DOC_NUMBER", "文档编号");
        DEFAULT_RULES.put("PART_NUMBER", "部件编号");
        DEFAULT_RULES.put("FUNCTIONAL-NUM", "功能架构编码规则");
        DEFAULT_RULES.put("ECAD_PROJECT-NUM", "电子设计项目编码");
    }

    private final NumberMapper numberMapper;
    private final NumberSegmentMapper numberSegmentMapper;

    public NumberRuleInitializer(NumberMapper numberMapper, NumberSegmentMapper numberSegmentMapper) {
        this.numberMapper = numberMapper;
        this.numberSegmentMapper = numberSegmentMapper;
    }

    @Override
    public void run(String... args) {
        String previousTenant = TenantContext.getOrNull();
        TenantContext.set(TenantContext.PLATFORM_TENANT_OID);
        try {
            int inserted = 0;
            int existing = 0;
            for (Map.Entry<String, String> entry : DEFAULT_RULES.entrySet()) {
                String ruleCode = entry.getKey();
                String ruleName = entry.getValue();
                try {
                    if (numberMapper.existsByCode(ruleCode) > 0) {
                        existing++;
                        log.debug("  编码规则 {} 已存在，跳过", ruleCode);
                        continue;
                    }
                    Number number = new Number(ruleCode, ruleName);
                    number.setOid(UUID.randomUUID().toString());
                    number.setEnabled(true);
                    number.setDescription(ruleName + "（系统预置）");
                    number.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                    numberMapper.insert(number);

                    for (NumberSegment seg : buildDefaultNumberSegments(ruleCode)) {
                        seg.setOid(UUID.randomUUID().toString());
                        seg.setRuleCode(ruleCode);
                        numberSegmentMapper.insert(seg);
                    }
                    inserted++;
                    log.info("  √ 编码规则已创建: {} ({})", ruleCode, ruleName);
                } catch (Exception e) {
                    // 单条失败不影响其余规则；编码规则是「用到才配」的能力，也不该让应用起不来
                    log.error("  ✗ 创建编码规则 {} 失败: {}", ruleCode, e.getMessage(), e);
                }
            }
            log.info("编码规则就绪（平台租户）: 新增 {} 条, 已存在 {} 条", inserted, existing);
        } finally {
            // 还原上下文：启动期本来就是「未设置」，清了才对；已设置则原样放回
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }
    }

    /** 各默认编码规则的段配置（编号示例见各项注释） */
    private List<NumberSegment> buildDefaultNumberSegments(String ruleCode) {
        List<NumberSegment> segments = new ArrayList<>();
        switch (ruleCode) {
            case "PRODUCT_LINE":
                // PL-001, PL-002...
                segments.add(new NumberSegment("CONST", "PL", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("SERIAL", 3, 1, 3));
                break;
            case "PRODUCT_MODEL":
                // PM-2026-001
                segments.add(new NumberSegment("CONST", "PM", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("SEPARATOR", "-", 4));
                segments.add(new NumberSegment("SERIAL", 3, 1, 5));
                break;
            case "DOC_NUMBER":
                // DOC-202601-0001
                segments.add(new NumberSegment("CONST", "DOC", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("SEPARATOR", "-", 5));
                segments.add(new NumberSegment("SERIAL", 4, 1, 6));
                break;
            case "PART_NUMBER":
                // PART-202601-0001
                segments.add(new NumberSegment("CONST", "PART", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("SEPARATOR", "-", 5));
                segments.add(new NumberSegment("SERIAL", 4, 1, 6));
                break;
            case "FUNCTIONAL-NUM":
                // FUNC-20260101-00000001
                segments.add(new NumberSegment("CONST", "FUNC", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("DAY", "dd", null, 5));
                segments.add(new NumberSegment("SERIAL", 8, 1, 6));
                break;
            case "ECAD_PROJECT-NUM":
                // ECAD-202601-0001
                segments.add(new NumberSegment("CONST", "ECAD", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("SEPARATOR", "-", 5));
                segments.add(new NumberSegment("SERIAL", 4, 1, 6));
                break;
            default:
                break;
        }
        return segments;
    }
}
