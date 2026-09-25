/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 工程数据引用链接（EPM Reference Link）—— 对齐 Windchill {@code wt.epm.structure.EPMReferenceLink}。
 *
 * <p>表达工程数据之间的<b>横向引用</b>（非装配归属）：一份文档引用了另一份文档 / 模型。
 * 对应表 {@code ck_eng_doc_ref_link}。
 *
 * <h3>与 {@link EngDocMemberLink} 的根本区别</h3>
 * <ul>
 *   <li>引用链接 = <b>横向引用</b>关系，构成文档间引用网；<b>无数量概念</b>。</li>
 *   <li>成员链接 = <b>组成/装配</b>关系，构成装配树；必有数量。</li>
 * </ul>
 * 二者均实现 {@link EngDocDependencyLink}（公共契约）。
 *
 * <h3>Windchill 属性对照</h3>
 * <table border="1">
 *   <tr><th>EPMReferenceLink</th><th>本类字段</th><th>说明</th></tr>
 *   <tr><td>roleA「referencedBy」</td><td>{@code referencedByIterationOid}</td><td>发起引用的<b>迭代</b>（Iterated 端）</td></tr>
 *   <tr><td>roleB「references」</td><td>{@code referencesMasterOid}</td><td>被引用的<b>主对象</b>（Mastered 端）</td></tr>
 *   <tr><td>{@code referenceType}（required）</td><td>{@code referenceType}</td><td>引用类型，必填，见 {@link EngDocRefType}</td></tr>
 *   <tr><td>{@link EngDocDependencyLink} 公共项</td><td>{@code asStoredChildName} 等</td><td>接口契约的四项公共属性</td></tr>
 * </table>
 *
 * <p>注：Windchill 中 {@code EPMReferenceLink} 的被引用端可为
 * {@code EPMDocumentMaster} <b>或</b> {@code WTDocumentMaster}（因 DocumentMaster 未实现 Mastered），
 * 故本类以 {@link #referencesType} 区分被引用对象类型。
 */
public class EngDocRefLink extends BaseEntity implements EngDocDependencyLink, TenantEntity {

    /** 数据表名 */
    public static final String TABLE = "ck_eng_doc_ref_link";

    /** {@link #referencesType} 取值：被引用对象为工程数据 */
    public static final String REFERENCES_TYPE_ENG_DOCUMENT = "ENG_DOCUMENT";

    /** {@link #referencesType} 取值：被引用对象为通用文档 */
    public static final String REFERENCES_TYPE_DOCUMENT = "DOCUMENT";

    // ==================== 角色（roleA / roleB） ====================

    /** 发起引用的迭代 oid（roleA「referencedBy」，关联 ck_eng_document_iteration.oid） */
    private String referencedByIterationOid;

    /** 被引用主对象 oid（roleB「references」，关联 ck_eng_document.oid 或 ck_document.oid） */
    private String referencesMasterOid;

    /** 被引用对象类型：{@link #REFERENCES_TYPE_ENG_DOCUMENT} / {@link #REFERENCES_TYPE_DOCUMENT} */
    private String referencesType = REFERENCES_TYPE_ENG_DOCUMENT;

    /** 引用类型（referenceType，required），取值见 {@link EngDocRefType} */
    private String referenceType = EngDocRefType.DEPENDENCY.name();

    // ==================== EngDocDependencyLink 契约 ====================

    /** 创建时「子」文档的名称（AS_STORED_CHILD_NAME） */
    private String asStoredChildName;

    /** 应用自定义依赖类型（DEP_TYPE），0 = UNSPECIFIED */
    private Integer depType = DEP_TYPE_UNSPECIFIED;

    /** 是否强依赖（REQUIRED，默认 false） */
    private boolean required;

    /** 链接唯一 ID（UNIQUE_LINK_ID） */
    private Long uniqueLinkId;

    // ==================== 租户 ====================

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public EngDocRefLink() {
        super();
    }

    // ==================== EngDocDependencyLink 实现 ====================

    @Override
    public String getRoleAOid() {
        return referencedByIterationOid;
    }

    @Override
    public String getRoleBOid() {
        return referencesMasterOid;
    }

    @Override
    public String getAsStoredChildName() {
        return asStoredChildName;
    }

    @Override
    public void setAsStoredChildName(String asStoredChildName) {
        this.asStoredChildName = asStoredChildName;
    }

    @Override
    public Integer getDepType() {
        return depType;
    }

    @Override
    public void setDepType(Integer depType) {
        this.depType = depType;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public Long getUniqueLinkId() {
        return uniqueLinkId;
    }

    @Override
    public void setUniqueLinkId(Long uniqueLinkId) {
        this.uniqueLinkId = uniqueLinkId;
    }

    // ==================== Getter / Setter ====================

    public String getReferencedByIterationOid() { return referencedByIterationOid; }
    public void setReferencedByIterationOid(String referencedByIterationOid) { this.referencedByIterationOid = referencedByIterationOid; }

    public String getReferencesMasterOid() { return referencesMasterOid; }
    public void setReferencesMasterOid(String referencesMasterOid) { this.referencesMasterOid = referencesMasterOid; }

    public String getReferencesType() { return referencesType; }
    public void setReferencesType(String referencesType) { this.referencesType = referencesType; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 被引用对象是否为通用文档（而非工程数据） */
    public boolean isReferencesDocument() {
        return REFERENCES_TYPE_DOCUMENT.equals(referencesType);
    }

    /** 引用类型枚举视图（无法识别时回退 {@link EngDocRefType#DEPENDENCY}） */
    public EngDocRefType referenceTypeEnum() {
        return EngDocRefType.fromCode(referenceType);
    }

    @Override
    public String toString() {
        return "EngDocRefLink{referencedByIterationOid='" + referencedByIterationOid
                + "', referencesMasterOid='" + referencesMasterOid
                + "', referencesType='" + referencesType
                + "', referenceType='" + referenceType
                + "', required=" + required + "}";
    }
}
