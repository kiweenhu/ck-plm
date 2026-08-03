/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.VersionRule;

import java.util.List;

/**
 * 版本规则服务接口。
 *
 * <p>两套能力：
 * <ul>
 *   <li><b>编号生成</b> — {@link #generateNextVersion(String)} 用于实体编码（如 DOC-202601-0001）</li>
 *   <li><b>大版本序列</b> — {@link #getFirstRevision(String)} / {@link #getNextRevision(String, String)}
 *       用于 Iteration 的大版本跃迁（如 A → B → C），序列来自规则定义中的字母段</li>
 * </ul>
 */
public interface VersionRuleService {

    /** 获取所有版本规则 */
    List<VersionRule> getAllRules();

    /** 根据 OID 获取规则 */
    VersionRule getRuleByOid(String oid);

    /** 根据 Code 获取规则 */
    VersionRule getRuleByCode(String code);

    /** 创建版本规则 */
    VersionRule createRule(VersionRule rule);

    /** 更新版本规则 */
    VersionRule updateRule(VersionRule rule);

    /** 删除版本规则 */
    void deleteRule(String oid);

    /** 生成下一个版本编码（用于实体编号，如 DOC-202601-0001） */
    String generateNextVersion(String code);

    /** 重置序号 */
    void resetSequence(String code, Long newValue);

    // ==================== 大版本序列（revision） ====================

    /**
     * 从规则定义中解析大版本序列，如 (A,B,C,D,E,F,G,H) → ["A","B","C","D","E","F","G","H"]
     *
     * @param ruleCode 规则编码
     * @return 有序大版本列表（空列表表示规则中无字母序列）
     */
    List<String> getRevisionSequence(String ruleCode);

    /**
     * 获取规则定义中的第一个大版本
     *
     * @param ruleCode 规则编码
     * @return 第一个大版本，如 "A"；若规则中无字母序列则返回 "A"
     */
    String getFirstRevision(String ruleCode);

    /**
     * 获取当前大版本的下一个大版本
     *
     * @param ruleCode        规则编码
     * @param currentRevision 当前大版本
     * @return 下一个大版本；若已是最后一个则返回 null
     */
    String getNextRevision(String ruleCode, String currentRevision);

    // ==================== 类型绑定解析 ====================

    /**
     * 根据 typeDefinitionCode 查找绑定的版本规则编码。
     *
     * @param typeCode 类型定义编码（如 DOCUMENT / PART）
     * @return 版本规则编码，未绑定则返回 null
     */
    String resolveVersionRuleCode(String typeCode);
}
