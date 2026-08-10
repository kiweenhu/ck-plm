/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * BOM 差异（BOM Diff），预计算相邻迭代之间的 BOM 结构差异。
 *
 * <p>检入时预计算 from 到 to 的 BOM 变更（新增/移除/修改），
 * 版本对比视图直接读取，不再两次全量查树。
 *
 * <h3>设计理念</h3>
 * <pre>
 * 每次检入生成新迭代时，自动计算与上一迭代的 BOM Diff。
 * diffJson 格式：{"added": [...], "removed": [...], "changed": [...]}
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration  1 ── N  BomDiff  (fromIterationOid → PartIteration.oid)
 * PartIteration  1 ── N  BomDiff  (toIterationOid   → PartIteration.oid)
 * (fromIterationOid, toIterationOid) 联合唯一
 * </pre>
 */
public class BomDiff extends BaseEntity implements TenantEntity {

    /** 源迭代 oid（关联 ck_part_iteration.oid） */
    private String fromIterationOid;

    /** 目标迭代 oid（关联 ck_part_iteration.oid） */
    private String toIterationOid;

    /** 差异 JSON（格式：{"added": [...], "removed": [...], "changed": [...]}） */
    private String diffJson;

    /** 新增行数 */
    private Integer addedCount;

    /** 移除行数 */
    private Integer removedCount;

    /** 修改行数 */
    private Integer changedCount;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public BomDiff() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getFromIterationOid() { return fromIterationOid; }
    public void setFromIterationOid(String fromIterationOid) { this.fromIterationOid = fromIterationOid; }

    public String getToIterationOid() { return toIterationOid; }
    public void setToIterationOid(String toIterationOid) { this.toIterationOid = toIterationOid; }

    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }

    public Integer getAddedCount() { return addedCount; }
    public void setAddedCount(Integer addedCount) { this.addedCount = addedCount; }

    public Integer getRemovedCount() { return removedCount; }
    public void setRemovedCount(Integer removedCount) { this.removedCount = removedCount; }

    public Integer getChangedCount() { return changedCount; }
    public void setChangedCount(Integer changedCount) { this.changedCount = changedCount; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    /**
     * 判断是否无差异。
     */
    public boolean isEmpty() {
        return (addedCount == null || addedCount == 0)
                && (removedCount == null || removedCount == 0)
                && (changedCount == null || changedCount == 0);
    }

    @Override
    public String toString() {
        return "BomDiff{from='" + fromIterationOid + "', to='" + toIterationOid
                + "', added=" + addedCount
                + ", removed=" + removedCount
                + ", changed=" + changedCount + "}";
    }
}
