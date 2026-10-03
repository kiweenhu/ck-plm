/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.VersionRule;
import cn.ck.plm.base.mapper.VersionRuleMapper;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 应用启动时初始化「版本规则」（{@code ck_version_rule}）—— 平台预置，写入<b>平台租户</b>，
 * 各租户共用（与编号规则 {@code ck_number} 的种子口径一致）。
 *
 * <h3>版本规则是什么</h3>
 * 这里放的是<b>大版本（revision）字母序列</b>：{@code VersionRuleService#getRevisionSequence} 解析
 * {@code (A,B,C,D)} / {@code (A-Z)} 得到序列，{@code getFirstRevision / getNextRevision} 据此推 A→B→C。
 *
 * <p><b>不要把编号规则塞进来</b>：编码/编号规则是另一套（{@code ck_number} + 编码段，
 * 由 {@link NumberRuleInitializer} 播种）。早前这里的默认值混进了 7 条编号样例
 * （DATE_SEQ / PREFIX_SEQ / DOC_NUMBER / PART_NUMBER / CR_NUMBER …）：它们没有字母序列、
 * code 还与 {@code ck_number} 撞名，界面上像版本规则、实则不是，已清理。
 *
 * <h3>顺序</h3>
 * {@code @Order(1)}：排在 {@code BusinessDomainInitializer}(0) 之后、{@code TypeDefinitionInitializer}(2) 之前 ——
 * 类型注册时会按 code 绑定版本规则（默认 {@code LETTER_26}），规则必须先就位。
 *
 * <h3>幂等性</h3>
 * 按 code 逐条判断，<b>缺哪条补哪条</b>；已存在的规则（含管理员在界面上改过的）不会被覆盖。
 * 历史上"只写进第 1 条、其余永远补不上"的残局也能自愈。
 *
 * <h3>租户</h3>
 * 启动期没有请求上下文，{@code TenantContext.get()} 会退化成「默认租户」—— 所以这里<b>显式</b>切到
 * 平台租户再写，结束时还原上下文。（早前 8 条规则被写进默认租户，而类型绑定在平台租户，
 * 两边错位导致规则在界面上等于不存在。）
 */
@Component
@Order(1)
public class VersionRuleInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VersionRuleInitializer.class);

    private final VersionRuleMapper versionRuleMapper;

    public VersionRuleInitializer(VersionRuleMapper versionRuleMapper) {
        this.versionRuleMapper = versionRuleMapper;
    }

    @Override
    public void run(String... args) {
        String previousTenant = TenantContext.getOrNull();
        TenantContext.set(TenantContext.PLATFORM_TENANT_OID);
        try {
            int existing = 0;
            int inserted = 0;
            for (VersionRule rule : defaultRules()) {
                if (versionRuleMapper.selectByCode(rule.getCode()) != null) {
                    existing++;
                    continue;
                }
                rule.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                if (rule.getCreator() == null) {
                    rule.setCreator("system");
                }
                rule.setUpdater("system");
                versionRuleMapper.insert(rule);
                inserted++;
            }
            log.info("版本规则就绪（平台租户）: 新增 {} 条, 已存在 {} 条", inserted, existing);
        } catch (Exception e) {
            // 初始化失败不能让应用起不来：版本规则是"用到才配"的能力，缺了只会影响版本推进
            log.warn("版本规则初始化失败: {}", e.getMessage());
        } finally {
            // 还原上下文：启动期本来就是"未设置"，清了才对；已设置则原样放回
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }
    }

    /**
     * 默认版本规则：<b>只保留一条经典 26 字母序列</b>（A-Z）——
     * 大版本从 A 推进到 Z，日常足够；不再预置多种样例，实施也不必启动后逐个删。
     */
    private List<VersionRule> defaultRules() {
        List<VersionRule> rules = new ArrayList<>();
        rules.add(rule("LETTER_26", "26位字母序列", "(A-Z)",
                "大版本字母序列，从 A 到 Z（类型默认绑定这条）"));
        return rules;
    }

    private VersionRule rule(String code, String name, String ruleDefinition, String description) {
        VersionRule rule = new VersionRule();
        rule.setOid(UUID.randomUUID().toString());
        rule.setCode(code);
        rule.setName(name);
        rule.setRuleDefinition(ruleDefinition);
        rule.setDescription(description);
        rule.setApplicableType("GENERAL");
        rule.setSequenceValue(0L);
        rule.setEnabled(true);
        return rule;
    }
}
