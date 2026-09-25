/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

/**
 * 工程数据依赖链接契约 —— 对齐 Windchill {@code wt.epm.structure.EPMDependencyLink}。
 *
 * <p>Windchill 中 {@code EPMDocument} 之间的链接采用「<b>一个接口 + 多个具体链接类（各自独立表）</b>」架构：
 * <pre>
 * EPMDependencyLink（接口：两个 EPMDocument 之间依赖关系的通用形式，<b>无独立表</b>）
 *     ├── EPMMemberLink    装配成员（父装配 uses 子件，必有 quantity）→ {@link EngDocMemberLink}
 *     └── EPMReferenceLink 横向引用（referencedBy 引用 references，必有 referenceType）→ {@link EngDocRefLink}
 * </pre>
 *
 * <p>本接口固定 {@code EPMDependencyLink} 约定的四项公共属性
 * （{@code asStoredChildName / depType / required / uniqueLinkId}），
 * 并提供只读的角色访问器；两个具体实体各自实现之。上层如需泛化处理「依赖」，
 * 应面向本接口编程 —— 与 Windchill 中面向 {@code EPMDependencyLink} 编程一致。
 *
 * <h3>角色约定（对齐 Windchill {@code IteratedUsageLink}）</h3>
 * <ul>
 *   <li><b>roleA</b>（{@code Iterated} 端）：成员链接 = 父装配的<b>迭代</b>；
 *       引用链接 = 发起引用的<b>迭代</b>。类型均为 {@link EngineeringDocumentIteration}。</li>
 *   <li><b>roleB</b>（{@code Mastered} 端）：成员链接 = 子件<b>主对象</b>；
 *       引用链接 = 被引用<b>主对象</b>。类型为 {@link EngineeringDocument}
 *       （引用链接亦可指向通用文档，见 {@link EngDocRefLink#getReferencesType()}）。</li>
 * </ul>
 *
 * <p>即：结构（BOM）与引用都是<b>「迭代 → 主对象」</b>方向，这是 Windchill
 * {@code IteratedUsageLink}（roleA = Iterated，roleB = Mastered）的固有语义 ——
 * 每个装配版本拥有自己的成员结构，但指向子件的<b>主对象</b>而非某个具体版本。
 */
public interface EngDocDependencyLink {

    /** {@code depType} 默认值：未指定（对齐 Windchill {@code EPMDependencyLink.UNSPECIFIED}） */
    int DEP_TYPE_UNSPECIFIED = 0;

    // ==================== 角色（只读） ====================

    /** 角色 A oid（{@code Iterated} 端）：装配迭代 oid / 发起引用的迭代 oid */
    String getRoleAOid();

    /** 角色 B oid（{@code Mastered} 端）：子件主对象 oid / 被引用主对象 oid */
    String getRoleBOid();

    // ==================== EPMDependencyLink 公共属性 ====================

    /**
     * 依赖链接创建时「子」文档当时的名称。
     *
     * <p>对应 Windchill {@code AS_STORED_CHILD_NAME}：应用通常会记住其引用文档的名称，
     * 该字段使应用在文档被重命名后仍能找到正确文档。
     */
    String getAsStoredChildName();

    void setAsStoredChildName(String asStoredChildName);

    /**
     * 创作应用自定义的依赖类型（整数）。
     *
     * <p>对应 Windchill {@code DEP_TYPE}，默认 {@link #DEP_TYPE_UNSPECIFIED}。
     */
    Integer getDepType();

    void setDepType(Integer depType);

    /**
     * 是否强依赖：创作应用<b>必须</b>拥有「子」文档才能处理「父」文档。
     *
     * <p>对应 Windchill {@code REQUIRED}，默认 {@code false}。
     */
    boolean isRequired();

    void setRequired(boolean required);

    /** 链接唯一 ID（对应 Windchill {@code UNIQUE_LINK_ID}） */
    Long getUniqueLinkId();

    void setUniqueLinkId(Long uniqueLinkId);
}
