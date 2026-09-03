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
 * 成组替代组头（BOM Substitute Group），参考成组替代（Group Substitute）模型。
 *
 * <p>描述"多个 BOM 行成员被一组替代物料整组替换"的<b>成组替代</b>关系。
 * 与一对一的全局替代（{@link cn.ck.plm.part.entity.PartAlternateLink}）和 BOM 行替代
 * （{@link BomSubstituteLink}）不同，成组替代的替换单位不是"一颗料"，而是"一组行"：
 * 原料侧挂多个 BOM 行成员，替代侧挂一组替代物料，每个成员带数量因子，
 * 组上带有"整组替换"约束——必须整组替换，拆开换任何一个都不成立。
 *
 * <p>组头挂在<b>父件迭代</b>级别（而非独立生命周期），随父件的变更流程受控。
 * 组的成员行可以分属不同状态，但组作为整体只有 {@code APPROVED} 才参与下游解析。
 * 继承 {@link WithoutVersionEntity}，不需要自身的版本控制。
 *
 * <h3>典型场景</h3>
 * <pre>
 * 降本改版：三个分立元件（电容+电阻+电感）被一颗集成模块整组替换
 * 结构配套：新型号连接器替换旧型号时，配套支架和线缆跟着换
 * 工艺升级：两个手工焊接件被一个 SMT 冲压件替换，数量比例 2:1
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartIteration           1 ── N  BomSubstituteGroup        (parentIterationOid → PartIteration.oid)
 * BomSubstituteGroup      1 ── N  BomSubstituteGroupMember  (groupOid → BomSubstituteGroup.oid)
 * </pre>
 *
 * <h3>成员侧（memberSide）</h3>
 * <pre>
 * SOURCE      原料侧 —— 被替换的原始 BOM 行成员（引用 BomLinks）
 * SUBSTITUTE  替代侧 —— 替换上去的替代物料（引用 Part）
 * </pre>
 *
 * <h3>组状态（status）</h3>
 * <pre>
 * DRAFT      草稿 —— 编辑中，不参与下游解析
 * APPROVED   已批准 —— 作为整体参与下游解析（整组替换）
 * OBSOLETE   已废弃 —— 不再使用
 * </pre>
 */
public class BomSubstituteGroup extends WithoutVersionEntity implements TenantEntity {

    /** 父件迭代 oid（关联 ck_part_iteration.oid，成组替代挂载点） */
    private String parentIterationOid;

    /** 组状态：DRAFT / APPROVED / OBSOLETE */
    private String status;

    /** 整组替换约束（true = 必须整组替换，不允许拆开换任何一个） */
    private Boolean atomicReplace;

    /** 是否启用 */
    private Boolean enabled;

    /** 生效性配置 JSONB（预留日期/批次/序列号生效性） */
    private String effectivityJson;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public BomSubstituteGroup() {
        super();
        this.status = "DRAFT";
        this.atomicReplace = true;
        this.enabled = true;
    }

    // ==================== Getter / Setter ====================

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getAtomicReplace() { return atomicReplace; }
    public void setAtomicReplace(Boolean atomicReplace) { this.atomicReplace = atomicReplace; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getEffectivityJson() { return effectivityJson; }
    public void setEffectivityJson(String effectivityJson) { this.effectivityJson = effectivityJson; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 判断组是否已批准（作为整体参与下游解析） */
    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }

    /** 判断是否启用 */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** 判断是否要求整组替换 */
    public boolean isAtomicReplace() {
        return atomicReplace == null || atomicReplace;
    }

    @Override
    public String toString() {
        return "BomSubstituteGroup{parentIterationOid='" + parentIterationOid
                + "', status='" + status
                + "', atomicReplace=" + atomicReplace
                + ", enabled=" + enabled + "}";
    }
}
