/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.VersionRule;
import cn.ck.plm.base.mapper.VersionRuleMapper;
import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeVersionRuleLink;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.TypeVersionRuleLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本规则服务实现
 */
@Service
public class VersionRuleServiceImpl implements VersionRuleService {

    private static final Logger log = LoggerFactory.getLogger(VersionRuleServiceImpl.class);

    @Autowired
    private VersionRuleMapper mapper;

    @Autowired
    private TypeDefinitionMapper typeDefinitionMapper;

    @Autowired
    private TypeVersionRuleLinkService typeVersionRuleLinkService;

    /**
     * 应用启动时自动初始化默认规则样例
     */
    @PostConstruct
    public void initDefaultRules() {
        // 检查是否已有规则
        if (mapper.count() > 0) {
            return;
        }

        // 初始化默认规则样例
        List<VersionRule> defaultRules = getDefaultRules();
        for (VersionRule rule : defaultRules) {
            mapper.insert(rule);
        }
    }

    /**
     * 获取默认规则样例
     */
    private List<VersionRule> getDefaultRules() {
        VersionRule rule1 = new VersionRule();
        rule1.setOid(UUID.randomUUID().toString());
        rule1.setName("8位字母序列");
        rule1.setCode("LETTER_8");
        rule1.setRuleDefinition("(A,B,C,D,E,F,G,H)");
        rule1.setDescription("8位大写字母序列，从 A 到 H");
        rule1.setApplicableType("GENERAL");
        rule1.setSequenceValue(0L);
        rule1.setEnabled(true);

        VersionRule rule2 = new VersionRule();
        rule2.setOid(UUID.randomUUID().toString());
        rule2.setName("日期-序号");
        rule2.setCode("DATE_SEQ");
        rule2.setRuleDefinition("(YYYYMMDD)-(SEQ:6)");
        rule2.setDescription("日期加6位序号，如 20260101-000001");
        rule2.setApplicableType("GENERAL");
        rule2.setSequenceValue(0L);
        rule2.setEnabled(true);

        VersionRule rule3 = new VersionRule();
        rule3.setOid(UUID.randomUUID().toString());
        rule3.setName("前缀-序号");
        rule3.setCode("PREFIX_SEQ");
        rule3.setRuleDefinition("(PREFIX:DOC)-(SEQ:4)");
        rule3.setDescription("固定前缀加4位序号，如 DOC-0001");
        rule3.setApplicableType("CK_DOCUMENT");
        rule3.setSequenceValue(0L);
        rule3.setEnabled(true);

        VersionRule rule4 = new VersionRule();
        rule4.setOid(UUID.randomUUID().toString());
        rule4.setName("产品型号编码");
        rule4.setCode("PRODUCT_MODEL");
        rule4.setRuleDefinition("(PREFIX:PM)-(YYYY)-(SEQ:3)");
        rule4.setDescription("产品型号编码，格式：PM-2026-001");
        rule4.setApplicableType("CK_PRODUCT_MODEL");
        rule4.setSequenceValue(0L);
        rule4.setEnabled(true);

        VersionRule rule5 = new VersionRule();
        rule5.setOid(UUID.randomUUID().toString());
        rule5.setName("文档编号");
        rule5.setCode("DOC_NUMBER");
        rule5.setRuleDefinition("(PREFIX:DOC)-(YYYYMM)-(SEQ:4)");
        rule5.setDescription("文档编号，格式：DOC-202601-0001");
        rule5.setApplicableType("CK_DOCUMENT");
        rule5.setSequenceValue(0L);
        rule5.setEnabled(true);

        VersionRule rule6 = new VersionRule();
        rule6.setOid(UUID.randomUUID().toString());
        rule6.setName("产品系列编码");
        rule6.setCode("PRODUCT_LINE");
        rule6.setRuleDefinition("(PREFIX:PL)-(SEQ:3)");
        rule6.setDescription("产品系列编码，格式：PL-001");
        rule6.setApplicableType("CK_PRODUCT_LINE");
        rule6.setSequenceValue(0L);
        rule6.setEnabled(true);

        VersionRule rule7 = new VersionRule();
        rule7.setOid(UUID.randomUUID().toString());
        rule7.setName("零件编号");
        rule7.setCode("PART_NUMBER");
        rule7.setRuleDefinition("(PREFIX:PART)-(A,B,C)-(SEQ:4)");
        rule7.setDescription("零件编号，格式：PART-A-0001");
        rule7.setApplicableType("CK_PART");
        rule7.setSequenceValue(0L);
        rule7.setEnabled(true);

        VersionRule rule8 = new VersionRule();
        rule8.setOid(UUID.randomUUID().toString());
        rule8.setName("变更单编号");
        rule8.setCode("CR_NUMBER");
        rule8.setRuleDefinition("(PREFIX:CR)-(YYYYMMDD)-(SEQ:3)");
        rule8.setDescription("变更单编号，格式：CR-20260101-001");
        rule8.setApplicableType("CK_CHANGE_REQUEST");
        rule8.setSequenceValue(0L);
        rule8.setEnabled(true);

        return Arrays.asList(rule1, rule2, rule3, rule4, rule5, rule6, rule7, rule8);
    }

    @Override
    public List<VersionRule> getAllRules() {
        return mapper.selectAll();
    }

    @Override
    public VersionRule getRuleByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    @Override
    public VersionRule getRuleByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    @Transactional
    public VersionRule createRule(VersionRule rule) {
        // 检查编码是否已存在
        if (mapper.existsByCode(rule.getCode()) > 0) {
            throw new IllegalArgumentException("版本规则已存在: " + rule.getCode());
        }
        // 设置默认值
        rule.setOid(UUID.randomUUID().toString());
        if (rule.getSequenceValue() == null) {
            rule.setSequenceValue(0L);
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        mapper.insert(rule);
        return rule;
    }

    @Override
    @Transactional
    public VersionRule updateRule(VersionRule rule) {
        VersionRule existing = mapper.selectByOid(rule.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("版本规则不存在: " + rule.getOid());
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "版本规则");
        // 如果修改了编码，检查新编码是否与其他规则冲突
        if (!existing.getCode().equals(rule.getCode())) {
            if (mapper.existsByCode(rule.getCode()) > 0) {
                throw new IllegalArgumentException("编码已存在: " + rule.getCode());
            }
        }
        mapper.update(rule);
        return rule;
    }

    @Override
    @Transactional
    public void deleteRule(String oid) {
        VersionRule existing = mapper.selectByOid(oid);
        if (existing != null) {
            TenantContext.requireEditPermission(existing.getTenantOid(), "版本规则");
        }
        mapper.deleteByOid(oid);
    }

    @Override
    @Transactional
    public String generateNextVersion(String code) {
        VersionRule rule = mapper.selectByCode(code);
        if (rule == null) {
            throw new IllegalArgumentException("版本规则不存在: " + code);
        }
        if (!rule.getEnabled()) {
            throw new IllegalStateException("版本规则已禁用: " + code);
        }

        // 自增序号
        Long newSeq = mapper.incrementAndGetSequence(code);

        // 根据规则定义生成编码
        return generateByRule(rule.getRuleDefinition(), newSeq);
    }

    @Override
    @Transactional
    public void resetSequence(String code, Long newValue) {
        VersionRule rule = mapper.selectByCode(code);
        if (rule == null) {
            throw new IllegalArgumentException("版本规则不存在: " + code);
        }
        rule.setSequenceValue(newValue);
        mapper.update(rule);
    }

    // ==================== 大版本序列（revision） ====================

    @Override
    public List<String> getRevisionSequence(String ruleCode) {
        VersionRule rule = mapper.selectByCode(ruleCode);
        if (rule == null) {
            throw new IllegalArgumentException("版本规则不存在: " + ruleCode);
        }
        return parseRevisionSequence(rule.getRuleDefinition());
    }

    @Override
    public String getFirstRevision(String ruleCode) {
        List<String> seq = getRevisionSequence(ruleCode);
        return seq.isEmpty() ? "A" : seq.get(0);
    }

    @Override
    public String getNextRevision(String ruleCode, String currentRevision) {
        if (currentRevision == null) return null;
        List<String> seq = getRevisionSequence(ruleCode);
        if (seq.isEmpty()) {
            // 规则中没有字母序列 → 回退到 char+1 行为
            char c = currentRevision.charAt(0);
            return String.valueOf((char) (c + 1));
        }
        int idx = seq.indexOf(currentRevision);
        if (idx < 0) return null;                       // 当前版本不在序列中
        if (idx + 1 >= seq.size()) return null;          // 已是最后一个
        return seq.get(idx + 1);
    }

    /**
     * 从规则定义中提取大版本字母序列。
     * 支持格式：(A,B,C,D,E,F) 或 (A-Z) 以及分隔符逗号/破折号等
     */
    List<String> parseRevisionSequence(String ruleDefinition) {
        // 匹配括号内的字母序列，如 (A,B,C,D,E,F,G,H) 或 (A-Z)
        Pattern p = Pattern.compile("\\(([A-Z]([,\\-][A-Z])*)\\)");
        Matcher m = p.matcher(ruleDefinition);
        if (m.find()) {
            String content = m.group(1);
            return Arrays.asList(content.split("[,]"));
        }
        // 也支持单个字母范围 (A-Z)
        Pattern range = Pattern.compile("\\(([A-Z])-([A-Z])\\)");
        Matcher rm = range.matcher(ruleDefinition);
        if (rm.find()) {
            char start = rm.group(1).charAt(0);
            char end = rm.group(2).charAt(0);
            List<String> result = new java.util.ArrayList<>();
            for (char c = start; c <= end; c++) {
                result.add(String.valueOf(c));
            }
            return result;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 根据规则定义生成编码
     */
    private String generateByRule(String ruleDefinition, Long sequence) {
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < ruleDefinition.length()) {
            char c = ruleDefinition.charAt(i);

            if (c == '(') {
                // 找到匹配的 )
                int end = findMatchingParen(ruleDefinition, i);
                String segment = ruleDefinition.substring(i + 1, end);

                result.append(processSegment(segment, sequence));

                i = end + 1;
            } else if (c == '-' || c == '_' || c == '/' || c == ':') {
                // 分隔符直接保留
                result.append(c);
                i++;
            } else {
                // 其他字符跳过（允许在括号外有其他内容）
                i++;
            }
        }

        return result.toString();
    }

    private int findMatchingParen(String str, int start) {
        int depth = 1;
        for (int i = start + 1; i < str.length(); i++) {
            if (str.charAt(i) == '(') depth++;
            else if (str.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return str.length() - 1;
    }

    @Override
    public String resolveVersionRuleCode(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) return null;
        try {
            TypeDefinition typeDef = typeDefinitionMapper.selectByCode(
                    typeCode.trim(), TenantContext.get(), TenantContext.PLATFORM_TENANT_OID);
            if (typeDef == null) return null;
            TypeVersionRuleLink link = typeVersionRuleLinkService.getByTypeOid(typeDef.getOid());
            return link != null ? link.getVersionRuleCode() : null;
        } catch (Exception e) {
            log.debug("查找版本规则失败: typeCode={}, error={}", typeCode, e.getMessage());
            return null;
        }
    }

    private String processSegment(String segment, Long sequence) {
        segment = segment.trim();

        // 日期格式 (YYYY, YYYYMM, YYYYMMDD, etc.)
        if (segment.matches("Y{1,4}M{1,2}D{1,2}")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(segment);
            return LocalDate.now().format(formatter);
        }

        // 序号格式 (SEQ:N)
        Pattern seqPattern = Pattern.compile("SEQ:?(\\d+)");
        Matcher seqMatcher = seqPattern.matcher(segment);
        if (seqMatcher.matches()) {
            int digits = Integer.parseInt(seqMatcher.group(1));
            return String.format("%0" + digits + "d", sequence);
        }

        // 前缀格式 (PREFIX:XXX)
        if (segment.startsWith("PREFIX:")) {
            return segment.substring(7);
        }

        // 字母序列 (A,B,C,D,...)
        if (segment.matches("[A-Z](,[A-Z])*") || segment.matches("[a-z](,[a-z])*")) {
            // 直接返回样例
            return segment.replace(",", "");
        }

        // 数字序列 (0-9)
        if (segment.matches("\\d(-\\d)*")) {
            return segment.replace("-", "");
        }

        // 未知格式，直接返回
        return segment;
    }
}
