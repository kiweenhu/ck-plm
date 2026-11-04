/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    // 默认版本规则的「种子」不在这里 —— 已挪到 electronics-core 行业包的 versionRules.json（VersionRuleInstaller 落地）：
    //   · 初始化器有明确的启动顺序（@Order），而 service 的 @PostConstruct 没有，容易在"租户上下文还没准备好"
    //     的时候写库（历史上就是这么把 8 条规则写进默认租户的）；
    //   · 种子与业务实现分离，service 只管规则的增删改查与版本推演。

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
        // 按租户隔离（2026-10-11）：归属必须显式落本租户（同 DefaultNumberService.create）
        if (rule.getTenantOid() == null) {
            rule.setTenantOid(TenantContext.get());
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

        // 自增序号：先 UPDATE 再读回（本方法 @Transactional，UPDATE 已持行锁 → 并发安全）。
        // 别用 "UPDATE … RETURNING"：MyBatis 走 executeUpdate 拿不到返回值，只会得到影响行数 1，
        // 于是序号恒为 1、生成结果永远是第一个字母。
        mapper.incrementSequence(code);
        Long newSeq = mapper.selectSequenceValue(code);
        if (newSeq == null) {
            throw new IllegalStateException("版本规则序号读取失败: " + code);
        }

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

    @Override
    public boolean isLastRevision(String ruleCode, String currentRevision) {
        if (currentRevision == null || currentRevision.trim().isEmpty()) return false;
        List<String> seq = getRevisionSequence(ruleCode);
        if (seq.isEmpty()) return false;                 // 规则无字母序列 → 不受限（保留 char+1 兜底）
        int idx = seq.indexOf(currentRevision);
        return idx >= 0 && idx + 1 >= seq.size();        // 在序列中且已是最后一项
    }

    /**
     * 从规则定义中提取大版本字母序列。
     *
     * <p>三种写法都要认（历史上都出现过）：
     * <ul>
     *   <li>范围：{@code (A-Z)}、{@code (A-D)}</li>
     *   <li>括号逗号序列：{@code (A,B,C)}</li>
     *   <li>裸逗号序列：{@code A,B,C,D} —— 平台种子 {@code LETTER_26} 就是这种写法；
     *       少了括号曾导致整条规则被当成"没有字母序列"，一路 char+1，到了 Z 之后既没有
     *       下一个字母、也无从判断"已是最后一个"</li>
     * </ul>
     *
     * <p>顺序不能反：{@code (A-Z)} 会被逗号序列的正则匹配成单个元素 "A-Z"，
     * 必须先单独展开范围，再匹配逗号序列。
     */
    List<String> parseRevisionSequence(String ruleDefinition) {
        if (ruleDefinition == null || ruleDefinition.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String def = ruleDefinition.trim();

        // 1. 字母范围 (A-Z)
        Matcher range = Pattern.compile("\\(([A-Z])\\s*-\\s*([A-Z])\\)").matcher(def);
        if (range.find()) {
            char start = range.group(1).charAt(0);
            char end = range.group(2).charAt(0);
            if (end < start) {
                return java.util.Collections.emptyList();
            }
            List<String> result = new java.util.ArrayList<>();
            for (char c = start; c <= end; c++) {
                result.add(String.valueOf(c));
            }
            return result;
        }

        // 2. 括号内的逗号序列 (A,B,C)
        Matcher m = Pattern.compile("\\(([A-Z](?:\\s*,\\s*[A-Z])*)\\)").matcher(def);
        if (m.find()) {
            return splitLetters(m.group(1));
        }

        // 3. 裸逗号序列 A,B,C（平台种子 LETTER_26）
        if (def.matches("[A-Z](?:\\s*,\\s*[A-Z])*")) {
            return splitLetters(def);
        }
        return java.util.Collections.emptyList();
    }

    /** 逗号分隔的字母串 → 列表（去空白，忽略空项） */
    private List<String> splitLetters(String content) {
        List<String> result = new java.util.ArrayList<>();
        for (String part : content.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
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

        // 字母序列 (A,B,C,D,...)：直接返回样例（作固定前缀用）
        if (segment.matches("[A-Z](,[A-Z])*") || segment.matches("[a-z](,[a-z])*")) {
            // 直接返回样例
            return segment.replace(",", "");
        }

        // 字母范围 (A-Z)：大版本序列的简写 —— 序号从 1 起，取第 N 个字母（seq=1 → A）。
        // 早前这里没有这个分支，落到末尾"未知格式直接返回"，于是生成结果就是字面量 "A-Z"
        // （版本规则页点"生成"会拿到它），而且序号已经被白烧掉一个。
        if (segment.matches("[A-Z]-[A-Z]")) {
            char start = segment.charAt(0);
            char end = segment.charAt(2);
            long idx = (sequence == null ? 1L : sequence) - 1;
            if (idx < 0) idx = 0;
            if (idx > end - start) {
                throw new IllegalStateException("版本序列已到末尾（" + start + "~" + end
                        + "），请调整版本规则或手动指定大版本");
            }
            return String.valueOf((char) (start + idx));
        }

        // 数字序列 (0-9)
        if (segment.matches("\\d(-\\d)*")) {
            return segment.replace("-", "");
        }

        // 未知格式，直接返回
        return segment;
    }
}
