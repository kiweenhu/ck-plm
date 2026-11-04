/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 工程数据 ↔ 零部件构建规则（EPM Build Rule）—— 对齐 Windchill {@code wt.epm.build.EPMBuildRule}。
 *
 * <p>表达「这份 CAD 数据描述/驱动哪个零件」：一份 3D 数模是某个零件的 Owner 数据，
 * 一张 2D 图纸是它的 Contributing Image。对应表 {@code ck_eng_doc_part_link}。
 *
 * <h3>挂接粒度</h3>
 * <p>关系挂在<b>零件迭代</b>（{@link #partIterationOid} → {@code ck_part_iteration.oid}）上，
 * 与 BOM、零件-文档关联保持同一粒度：同一零件的不同版本可以指向不同版本的数模。
 * 工程数据侧同时保留主对象（{@link #engDocumentOid}）与精确迭代
 * （{@link #engDocIterationOid}，可空）：为空表示「跟随最新」，非空表示锁定到某一版。
 *
 * <h3>与 ck_doc_part_link 的区别</h3>
 * <ul>
 *   <li>{@code ck_doc_part_link} —— 零件 ↔ <b>通用文档</b>（规格书/报告），语义是 DESCRIBES / REFERENCE；</li>
 *   <li>本表 —— 零件 ↔ <b>工程数据</b>（3D 数模 / 2D 工程图 / 封装 / 图符），语义是构建规则。</li>
 * </ul>
 * 两者不要混用：一个回答「谁规定了它」，一个回答「它是从哪份 CAD 造出来的」。
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration          1 ── N  EngDocPartLink  (partIterationOid   → PartIteration.oid)
 * EngineeringDocument    1 ── N  EngDocPartLink  (engDocumentOid     → EngineeringDocument.oid)
 * EngineeringDocumentIteration 1 ── N (engDocIterationOid → 该表 oid，可空)
 * </pre>
 */
public class EngDocPartLink extends BaseEntity implements TenantEntity {

    /** 数据表名 */
    public static final String TABLE = "ck_eng_doc_part_link";

    /** 关联类型：主数据（零件由此份数模构建，Windchill 中每个零件至多 1 个） */
    public static final String ASSOC_OWNER = "OWNER";

    /** 关联类型：内容（普通内容关联，默认） */
    public static final String ASSOC_CONTENT = "CONTENT";

    /** 关联类型：对主数据有贡献的图纸（如 2D 工程图，每个零件至多 1 个） */
    public static final String ASSOC_CONTRIBUTING_IMAGE = "CONTRIBUTING_IMAGE";

    /** 关联类型：附图 */
    public static final String ASSOC_IMAGE = "IMAGE";

    /** 全部合法关联类型 */
    private static final String[] ASSOC_TYPES = {
            ASSOC_OWNER, ASSOC_CONTENT, ASSOC_CONTRIBUTING_IMAGE, ASSOC_IMAGE
    };

    // ==================== 关联两端 ====================

    /** 工程数据主对象 oid（关联 ck_eng_document.oid） */
    private String engDocumentOid;

    /** 工程数据精确迭代 oid（关联 ck_eng_document_iteration.oid，NULL = 跟随最新） */
    private String engDocIterationOid;

    /** 零件迭代 oid（关联 ck_part_iteration.oid） */
    private String partIterationOid;

    // ==================== 构建规则 ====================

    /** 关联类型，取值见 {@link #ASSOC_OWNER} 等常量 */
    private String assocType = ASSOC_CONTENT;

    /** 构建结构（BOM 由该份数模驱动） */
    private boolean buildStructure;

    /** 构建属性（零件属性由该份数模回写） */
    private boolean buildAttribute;

    /** 构建表示（可视化/轻量化由该份数模生成） */
    private boolean buildRepresentation;

    /** 关联来源：MANUAL（人工）/ IMPORT（迁移导入）/ DERIVED（自动推导） */
    private String assocSource = "MANUAL";

    /** 是否主关联（同一零件版本下优先展示/优先取用） */
    private boolean primary;

    /** 排序号 */
    private int sortOrder;

    // ==================== 租户 ====================

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public EngDocPartLink() {
        super();
    }

    // ==================== 便捷方法 ====================

    /** 关联类型是否合法 */
    public static boolean isValidAssocType(String assocType) {
        if (assocType == null) return false;
        for (String type : ASSOC_TYPES) {
            if (type.equals(assocType)) return true;
        }
        return false;
    }

    /** 是否为精确引用（锁定了工程数据的某个具体迭代） */
    public boolean isPinned() {
        return engDocIterationOid != null && !engDocIterationOid.isEmpty();
    }

    /** 是否为主数据关联（每个零件版本至多一个） */
    public boolean isOwnerLink() {
        return ASSOC_OWNER.equals(assocType);
    }

    // ==================== Getter / Setter ====================

    public String getEngDocumentOid() { return engDocumentOid; }
    public void setEngDocumentOid(String engDocumentOid) { this.engDocumentOid = engDocumentOid; }

    public String getEngDocIterationOid() { return engDocIterationOid; }
    public void setEngDocIterationOid(String engDocIterationOid) { this.engDocIterationOid = engDocIterationOid; }

    public String getPartIterationOid() { return partIterationOid; }
    public void setPartIterationOid(String partIterationOid) { this.partIterationOid = partIterationOid; }

    public String getAssocType() { return assocType; }
    public void setAssocType(String assocType) { this.assocType = assocType; }

    public boolean isBuildStructure() { return buildStructure; }
    public void setBuildStructure(boolean buildStructure) { this.buildStructure = buildStructure; }

    public boolean isBuildAttribute() { return buildAttribute; }
    public void setBuildAttribute(boolean buildAttribute) { this.buildAttribute = buildAttribute; }

    public boolean isBuildRepresentation() { return buildRepresentation; }
    public void setBuildRepresentation(boolean buildRepresentation) { this.buildRepresentation = buildRepresentation; }

    public String getAssocSource() { return assocSource; }
    public void setAssocSource(String assocSource) { this.assocSource = assocSource; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "EngDocPartLink{partIterationOid='" + partIterationOid
                + "', engDocumentOid='" + engDocumentOid
                + "', engDocIterationOid='" + engDocIterationOid
                + "', assocType='" + assocType + "'}";
    }
}
