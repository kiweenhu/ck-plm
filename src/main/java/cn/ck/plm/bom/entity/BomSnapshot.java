/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * BOM 快照（BOM Snapshot）：某一时刻父件迭代的顶层 BOM 行（子件清单）定格。
 *
 * <p>用户在产品结构页手动创建、可命名，用于「这段时间 BOM 改了什么」的对比基线。
 * 同一迭代允许多份（检出状态下 BOM 行仍会增删改，"一个迭代一份"不成立）。
 *
 * <h3>与其它对象的关系</h3>
 * <pre>
 * PartIteration  1 ── N  BomSnapshot   (iterationOid → PartIteration.oid)
 * </pre>
 *
 * <p>快照只读：创建后 BOM 再变也不会回写它 —— 这是「快照 vs 当前」对比能成立的前提。
 */
public class BomSnapshot extends BaseEntity implements TenantEntity {

    /** 迭代 oid（外键，关联 ck_part_iteration.oid；删迭代时快照级联删除） */
    private String iterationOid;

    /** 父件主对象 oid（冗余一列：列表按零件聚合，省一次 join） */
    private String partOid;

    /** 快照名称（用户填写，如「设计基线」「评审冻结」） */
    private String name;

    /** 备注（可选） */
    private String remark;

    /** 快照建立时父件迭代的显示版本（A.2），列表直接展示 */
    private String versionLabel;

    /**
     * BOM 行快照 JSON：{@code {"items":[{lineNumber,childPartOid,number,name,version,quantity,unit,unitCost}]}}。
     *
     * <p>与 {@code BomDiffServiceImpl} 的差异行字段同名 —— 对比结果可以直接复用同一套前端渲染。
     */
    private String snapshotJson;

    /** 顶层 BOM 行数（子件条数） */
    private Integer itemCount;

    /** 展开后节点总数（含各级孙子件） */
    private Integer nodeCount;

    /** 树最大深度 */
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

    public String getPartOid() { return partOid; }
    public void setPartOid(String partOid) { this.partOid = partOid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

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
        return "BomSnapshot{name='" + name + "', iterationOid='" + iterationOid
                + "', itemCount=" + itemCount + "}";
    }
}
