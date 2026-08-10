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
 * BOM 快照（BOM Snapshot），为 RELEASED 迭代预计算整棵 BOM 树的 JSONB 快照。
 *
 * <p>与 {@link cn.ck.plm.part.entity.PartIteration} 一对一关系，
 * iterationOid 既是主键也是外键。已发布 BOM 的渲染 = 一行读取，零递归。
 *
 * <h3>设计理念</h3>
 * <pre>
 * 核心三表解决"对不对"，快照表解决"快不快"。
 * 系统刚上线时只跑核心三表就能工作；数据量变大、BOM 渲染变慢时再引入快照。
 * 复杂度按需生长，不动已有结构。
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration  1 ── 0..1  BomSnapshot   (iterationOid → PartIteration.oid)
 * </pre>
 */
public class BomSnapshot extends BaseEntity implements TenantEntity {

    /** 迭代 oid（主键 + 外键，关联 ck_part_iteration.oid） */
    private String iterationOid;

    /** 整棵 BOM 树的 JSONB 快照 */
    private String snapshotJson;

    /** BOM 树节点总数 */
    private Integer nodeCount;

    /** BOM 树最大深度 */
    private Integer maxDepth;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public BomSnapshot() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public Integer getNodeCount() { return nodeCount; }
    public void setNodeCount(Integer nodeCount) { this.nodeCount = nodeCount; }

    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "BomSnapshot{iterationOid='" + iterationOid
                + "', nodeCount=" + nodeCount
                + ", maxDepth=" + maxDepth + "}";
    }
}
