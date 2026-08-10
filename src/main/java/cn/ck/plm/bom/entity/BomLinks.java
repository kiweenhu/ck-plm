/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.entity;

import cn.ck.plm.base.entity.WithoutVersionEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * BOM 行项（BOM Links），参考 Windchill WTPartUsageLink 模型。
 *
 * <p>描述一个部件的 BOM 构成关系：父部件某个迭代 → 子部件 + 数量/单位/行号。
 * BOM 行挂在父迭代级别，迭代本身已携带 view 信息，因此 BOM 行不需要单独的 view 列。
 * 继承 {@link WithoutVersionEntity}，不需要自身的版本控制。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * WTPartUsageLink  →  BomLinks
 * parent_iteration_id → 父迭代（迭代已携带 version + iteration + view）
 * child_part_id       → 子部件主对象
 * child_iteration_id  → 子部件精确迭代（可空，NULL = 非精确引用，跟随最新）
 * resolved_iteration_id → 非精确引用的解析缓存，渲染时直接 JOIN，避免每行子查询
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration  1 ── N  BomLinks        (parentIterationOid → PartIteration.oid)
 * Part           1 ── N  BomLinks        (childPartOid → Part.oid)
 * PartIteration  1 ── N  BomLinks        (childIterationOid → PartIteration.oid, nullable)
 * PartIteration  1 ── N  BomLinks        (resolvedIterationOid → PartIteration.oid, 解析缓存)
 * </pre>
 *
 * <h3>精确引用 vs 非精确引用</h3>
 * <pre>
 * 精确引用：childIterationOid NOT NULL → 锁定到子件某个迭代，适用已发布基线、合规追溯
 * 非精确引用：childIterationOid NULL → 始终取子件最新迭代，适用设计阶段、快速迭代
 * </pre>
 */
public class BomLinks extends WithoutVersionEntity implements TenantEntity {

    /** 父部件迭代 oid（关联 ck_part_iteration.oid，迭代本身已携带 view） */
    private String parentIterationOid;

    /** 子部件主对象 oid（关联 ck_part.oid） */
    private String childPartOid;

    /** 子部件精确迭代 oid（关联 ck_part_iteration.oid，可空。NULL = 非精确引用，跟随最新） */
    private String childIterationOid;

    /** 非精确引用的解析缓存（childIterationOid 为 NULL 时，缓存解析到的最新迭代 oid） */
    private String resolvedIterationOid;

    /** 用量 */
    private Double quantity;

    /** 单位 */
    private String unit;

    /** 行号 */
    private Integer lineNumber;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public BomLinks() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public String getChildPartOid() { return childPartOid; }
    public void setChildPartOid(String childPartOid) { this.childPartOid = childPartOid; }

    public String getChildIterationOid() { return childIterationOid; }
    public void setChildIterationOid(String childIterationOid) { this.childIterationOid = childIterationOid; }

    public String getResolvedIterationOid() { return resolvedIterationOid; }
    public void setResolvedIterationOid(String resolvedIterationOid) { this.resolvedIterationOid = resolvedIterationOid; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    /**
     * 判断是否为精确引用（锁定了子件的特定迭代）。
     */
    public boolean isExactReference() {
        return childIterationOid != null && !childIterationOid.isEmpty();
    }

    @Override
    public String toString() {
        return "BomLinks{parentIterationOid='" + parentIterationOid
                + "', childPartOid='" + childPartOid
                + "', childIterationOid='" + childIterationOid
                + "', quantity=" + quantity
                + ", unit='" + unit
                + "', lineNumber=" + lineNumber + "}";
    }
}
