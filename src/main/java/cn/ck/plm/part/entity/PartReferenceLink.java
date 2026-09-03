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
 * 零件「参考」文档关系（Part Reference Link），描述一份文档对某个零件构成<b>参考（REFERENCE）</b>。
 *
 * <p>与 {@link PartDescribeLink}（DESCRIBES）共同构成零件-文档关系的两分模型，二者共享同一张
 * 数据表 {@code ck_doc_part_link}，通过 {@code link_type} 列区分。判定准则见项目文档
 * 《一份仿真报告，算不算零件的"定义"？》：
 *
 * <blockquote>
 * 当且仅当"文档的变更后果 = 零件的变更后果"时，它是 DESCRIBES；否则是 REFERENCE。
 * </blockquote>
 *
 * <h3>三问判定（任一问不通过即 REFERENCE）</h3>
 * <ol>
 *   <li>纯记录 / 纯分析 / 纯意见 —— 不构成约束 → REFERENCE</li>
 *   <li>通用工艺规范、国标行标 —— 约束所有零件、非专属 → REFERENCE</li>
 *   <li>约束的最终载体是别的文档（如结论已转写进规格书）→ REFERENCE</li>
 * </ol>
 *
 * <h3>典型 REFERENCE</h3>
 * <ul>
 *   <li>研发验证报告（结论支撑设计决策，放行判据在规格书）</li>
 *   <li>仿真报告（结论已入规格书，挂零件上保留 Know-Why）</li>
 *   <li>评审记录、通用规范、设计手册</li>
 * </ul>
 *
 * <h3>动态晋升</h3>
 * <p>设计早期，分析文档以 REFERENCE 伴随零件快速迭代（轻量、不打扰）；设计冻结时，结论被采纳
 * 为规格、文档晋升为 {@link PartDescribeLink}（DESCRIBES）并锁定精确版本——即「进入技术状态基线」。
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration  1 ── N  PartReferenceLink  (partIterationOid → PartIteration.oid)
 * Document       1 ── N  PartReferenceLink  (docMasterOid     → Document.oid)
 * </pre>
 */
public class PartReferenceLink extends BaseEntity implements TenantEntity {

    /** 关系类型常量：参考 */
    public static final String LINK_TYPE = "REFERENCE";

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

    public PartReferenceLink() {
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
        return "PartReferenceLink{partIterationOid='" + partIterationOid
                + "', docMasterOid='" + docMasterOid
                + "', docIterationOid='" + docIterationOid
                + "', category='" + category + "'}";
    }
}
