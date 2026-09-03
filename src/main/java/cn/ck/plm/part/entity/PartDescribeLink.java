/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 零件「定义」文档关系（Part Describe Link），描述一份文档对某个零件构成<b>定义（DESCRIBES）</b>。
 *
 * <p>与 {@link PartReferenceLink}（REFERENCE）共同构成零件-文档关系的两分模型，二者共享同一张
 * 数据表 {@code ck_doc_part_link}，通过 {@code link_type} 列区分。判定准则见项目文档
 * 《一份仿真报告，算不算零件的"定义"？》：
 *
 * <blockquote>
 * 判定一份文档属于哪边，不看它是什么，要看它变了会怎样。当且仅当
 * "文档的变更后果 = 零件的变更后果"时，它是 DESCRIBES。
 * </blockquote>
 *
 * <h3>三问判定（DESCRIBES 需全过）</h3>
 * <ol>
 *   <li>构成约束？—— 规定零件「必须是什么、必须怎么造、必须怎么验」</li>
 *   <li>零件专属？—— 约束只针对「这一个」零件，而非通用规范/国标行标</li>
 *   <li>最终载体？—— 约束的最终落点在此文档，而非「结论已转写进规格书」的佐证材料</li>
 * </ol>
 *
 * <h3>挂接粒度</h3>
 * <p>关系挂在<b>零件迭代</b>级别（{@code partIterationOid}，对齐 BOM 的挂法），而非主对象。
 * 文档侧同时保留主对象（{@code docMasterOid}）与精确迭代（{@code docIterationOid}，可空）。
 * {@code docIterationOid = null} 表示「跟随最新」；{@code resolvedIterationOid} 为解析缓存，
 * 渲染时直接 JOIN，避免运行时再查。
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration  1 ── N  PartDescribeLink  (partIterationOid → PartIteration.oid)
 * Document       1 ── N  PartDescribeLink  (docMasterOid     → Document.oid)
 * </pre>
 *
 * <h3>与 PartReferenceLink 的关系</h3>
 * <p>两者字段完全一致，仅 {@code linkType} 不同。业务上 REFERENCE 可<b>显式晋升</b>为 DESCRIBES
 * （进入技术状态基线），这是受控动作，需留痕。
 */
public class PartDescribeLink extends BaseEntity implements TenantEntity {

    /** 关系类型常量：定义 */
    public static final String LINK_TYPE = "DESCRIBES";

    /** 零件迭代 oid（关联 ck_part_iteration.oid，挂接粒度对齐 BOM） */
    private String partIterationOid;

    /** 文档主对象 oid（关联 ck_document.oid） */
    private String docMasterOid;

    /** 文档精确迭代 oid（关联 ck_document_iteration.oid，NULL = 跟随最新） */
    private String docIterationOid;

    /** 解析缓存迭代 oid（非精确引用时缓存解析到的最新文档迭代） */
    private String resolvedIterationOid;

    /** 文档类别（图纸/规格书/报告等，软类型驱动） */
    private String category;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartDescribeLink() {
        super();
    }

    // ==================== Getter / Setter ====================

    /** 关系类型（恒为 {@value LINK_TYPE}） */
    public String getLinkType() { return LINK_TYPE; }

    public String getPartIterationOid() { return partIterationOid; }
    public void setPartIterationOid(String partIterationOid) { this.partIterationOid = partIterationOid; }

    public String getDocMasterOid() { return docMasterOid; }
    public void setDocMasterOid(String docMasterOid) { this.docMasterOid = docMasterOid; }

    public String getDocIterationOid() { return docIterationOid; }
    public void setDocIterationOid(String docIterationOid) { this.docIterationOid = docIterationOid; }

    public String getResolvedIterationOid() { return resolvedIterationOid; }
    public void setResolvedIterationOid(String resolvedIterationOid) { this.resolvedIterationOid = resolvedIterationOid; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 是否为精确引用（锁定了文档的某个具体迭代） */
    public boolean isPinned() {
        return docIterationOid != null && !docIterationOid.isEmpty();
    }

    @Override
    public String toString() {
        return "PartDescribeLink{partIterationOid='" + partIterationOid
                + "', docMasterOid='" + docMasterOid
                + "', docIterationOid='" + docIterationOid
                + "', category='" + category + "'}";
    }
}
