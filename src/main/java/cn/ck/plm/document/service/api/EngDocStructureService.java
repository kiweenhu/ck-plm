/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.api;

import cn.ck.plm.document.entity.EngDocDependencyLink;
import cn.ck.plm.document.entity.EngDocMemberLink;
import cn.ck.plm.document.entity.EngDocRefLink;
import cn.ck.plm.document.entity.EngDocRefType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * 工程数据结构服务 —— 对齐 Windchill {@code wt.epm.structure.EPMStructureService}。
 *
 * <p>Windchill 的 {@code EPMStructureService} 提供沿 {@code EPMMemberLink} /
 * {@code EPMReferenceLink} 的<b>导航器（navigators）</b>，且可导航到
 * <b>master 与 iteration 两个层面</b>。本服务按同一思路组织两组导航：
 *
 * <pre>
 * 装配结构（EPMMemberLink，迭代 → 主对象）
 *     childrenOf(装配迭代)     向下：某装配迭代的直接成员（子件主对象）
 *     parentsOf(子件主对象)    向上：某子件被哪些装配迭代所使用
 *
 * 横向引用（EPMReferenceLink，迭代 → 主对象）
 *     referencesOf(迭代)       正向：某迭代引用了哪些对象
 *     referencedBy(主对象)     反向：某主对象被哪些迭代引用（影响分析入口）
 *     referencesOfType(类型)   按引用类型检索
 * </pre>
 *
 * <p><b>为什么是「迭代 → 主对象」</b>：Windchill 中这两类链接都继承/对齐
 * {@code IteratedUsageLink}（roleA = {@code Iterated}，roleB = {@code Mastered}）——
 * 每个装配<b>版本</b>拥有自己的成员结构，但指向子件的<b>主对象</b>（不锁定具体版本）。
 * 这与项目既有的 {@code ck_doc_part_link}（part 侧为迭代、doc 侧为主对象 + 可选精确迭代）一致。
 *
 * <p>递归导航需要把「子件主对象」解析为其具体迭代才能继续向下，而工程数据主对象↔迭代的解析
 * 属于 {@code EngineeringDocument} 服务职责，故此处以 {@code masterToIteration} 解析函数注入，
 * 避免本服务反向依赖上层。
 */
public interface EngDocStructureService {

    // ==================== 装配结构（EPMMemberLink） ====================

    /** 新增成员：父装配迭代 + 子件主对象 + 用量（quantity 为 null 时按 1 处理） */
    EngDocMemberLink addMember(String assemblyIterationOid, String childMasterOid, BigDecimal quantity);

    /** 新增成员（完整对象；校验 «带 transform ⇒ quantity=1 且 placed=true» 不变量） */
    EngDocMemberLink addMember(EngDocMemberLink link);

    /** 更新成员 */
    EngDocMemberLink updateMember(EngDocMemberLink link);

    /** 删除成员链接；返回是否删除成功 */
    boolean removeMember(String linkOid);

    /** 清空某装配迭代下的全部成员（整树重建用）；返回删除条数 */
    int clearMembers(String assemblyIterationOid);

    /** 向下导航：某装配迭代的<b>直接</b>成员列表（按 sort_order） */
    List<EngDocMemberLink> childrenOf(String assemblyIterationOid);

    /** 向上导航：某子件主对象被哪些装配迭代所使用 */
    List<EngDocMemberLink> parentsOf(String childMasterOid);

    // ==================== 横向引用（EPMReferenceLink） ====================

    /** 新增引用：发起引用的迭代 + 被引用主对象 + 引用类型 */
    EngDocRefLink addReference(String referencedByIterationOid, String referencesMasterOid, EngDocRefType type);

    /** 新增引用（完整对象） */
    EngDocRefLink addReference(EngDocRefLink link);

    /** 更新引用 */
    EngDocRefLink updateReference(EngDocRefLink link);

    /** 删除引用链接；返回是否删除成功 */
    boolean removeReference(String linkOid);

    /** 清空某迭代发出的全部引用；返回删除条数 */
    int clearReferences(String referencedByIterationOid);

    /** 正向导航：某迭代引用了哪些对象 */
    List<EngDocRefLink> referencesOf(String referencedByIterationOid);

    /** 反向导航：某主对象被哪些迭代引用 */
    List<EngDocRefLink> referencedBy(String referencesMasterOid);

    /** 按引用类型检索 */
    List<EngDocRefLink> referencesOfType(EngDocRefType type);

    // ==================== 递归导航 ====================

    /**
     * 递归展开装配树，返回全部后代<b>主对象</b> oid（不含起点）。
     *
     * @param assemblyIterationOid 起点装配迭代
     * @param maxDepth             最大展开层数（&lt;=0 时按 1 处理，仅直接成员）
     * @param masterToIteration    主对象 → 迭代 的解析函数；为 {@code null} 时仅展开一层
     */
    List<String> expandDescendantMasters(String assemblyIterationOid, int maxDepth,
                                         Function<String, String> masterToIteration);

    /**
     * 递归收集引用依赖（沿引用网），返回全部被（间接）引用的<b>主对象</b> oid（不含起点）。
     *
     * @param rootIterationOid  起点迭代
     * @param maxDepth          最大展开层数（&lt;=0 时按 1 处理）
     * @param masterToIteration 主对象 → 迭代 的解析函数；为 {@code null} 时仅展开一层
     */
    List<String> collectDependencyMasters(String rootIterationOid, int maxDepth,
                                          Function<String, String> masterToIteration);

    // ==================== 泛化（面向契约接口） ====================

    /**
     * 某迭代的<b>全部</b>依赖链接（装配成员 + 横向引用）。
     *
     * <p>面向 {@link EngDocDependencyLink} 契约，与 Windchill 中「面向
     * {@code EPMDependencyLink} 编程以泛化处理依赖」一致。
     */
    List<EngDocDependencyLink> allDependenciesOfIteration(String iterationOid);
}
