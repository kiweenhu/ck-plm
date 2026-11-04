/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.VersionRule;
import cn.ck.plm.base.mapper.VersionRuleMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 版本规则安装器（{@code VERSION_RULE}）—— 读包内 {@code versionRules.json} 的 {@code items[]}。
 *
 * <p><b>数据来源变迁</b>：原先写在 {@code VersionRuleInitializer}，现随 {@code electronics-core} 包播种。
 *
 * <p><b>版本规则是什么</b>：大版本（revision）的字母序列，如 {@code (A-Z)}。
 * 它<b>不是</b>编码规则（{@code ck_number}）—— 两者 code 可能同名却语义不同（历史上混过一次，
 * 界面上出现过"像版本规则、实则编号规则"的脏数据），所以两者由不同安装器、不同模板文件承载。
 *
 * <p><b>幂等</b>：按 code 判重；已存在（含管理员改过的）不覆盖。软件版本规则（SEMVER）
 * 这类行业增量，由行业包在 P3 追加到自己的 {@code versionRules.json} 里。
 */
@Component
public class VersionRuleInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(VersionRuleInstaller.class);

    private final VersionRuleMapper versionRuleMapper;

    public VersionRuleInstaller(VersionRuleMapper versionRuleMapper) {
        this.versionRuleMapper = versionRuleMapper;
    }

    @Override
    public String resourceType() {
        return "VERSION_RULE";
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
                    "模板里没有 items 段（本包不含版本规则）"));
            return results;
        }

        for (Object element : (List<?>) items) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            try {
                if (versionRuleMapper.selectByCode(code) != null) {
                    results.add(InstallResult.skipped(resourceType(), code, "版本规则已存在"));
                    continue;
                }
                String name = text(row.get("name"));
                String definition = text(row.get("ruleDefinition"));
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增版本规则「" + name + "」" + definition));
                    continue;
                }
                VersionRule rule = new VersionRule();
                rule.setOid(UUID.randomUUID().toString());
                rule.setCode(code);
                rule.setName(name);
                rule.setRuleDefinition(definition);
                rule.setDescription(text(row.get("description")));
                rule.setApplicableType(text(row.get("applicableType")) == null
                        ? "GENERAL" : text(row.get("applicableType")));
                rule.setSequenceValue(longOr(row.get("sequenceValue"), 0L));
                rule.setEnabled(!Boolean.FALSE.equals(row.get("enabled")));
                rule.setTenantOid(context.getTenantOid());
                if (rule.getCreator() == null) {
                    rule.setCreator("system");
                }
                rule.setUpdater("system");
                versionRuleMapper.insert(rule);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("版本规则：" + name + " " + definition)
                        .on("ck_version_rule", rule.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("版本规则安装完成: {} 条明细", results.size());
        return results;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static long longOr(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
