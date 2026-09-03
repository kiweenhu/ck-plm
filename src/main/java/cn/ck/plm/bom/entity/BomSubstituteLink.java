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
 * BOM 行替代件（BOM Substitute Link），参考 Windchill WTPartSubstituteLink 模型。
 *
 * <p>描述某个 BOM 行（{@link BomLinks}）中子部件可被替代件替换的<b>局部</b>替代关系。
 * 与全局双向替代（{@link cn.ck.plm.part.entity.PartAlternateLink}）不同，BOM 行替代
 * 限定在特定 BOM 结构内：仅在父部件该 BOM 行的上下文中，子部件才允许被该替代件替换。
 *
 * <p>BOM 行替代挂在 BOM 行（UsageLink）级别，随父件迭代受控（父件升版、替代关系跟着
 * 检入检出、走同一个变更审批）。继承 {@link WithoutVersionEntity}，不需要自身的版本控制。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * WTPartSubstituteLink   → BomSubstituteLink
 * parentUsageLink        → bomLinkOid           （父 BOM 行）
 * roleAObjectReference   → sourcePartOid        （原子部件，被替代方）
 * roleBObjectReference   → substitutePartOid    （替代件）
 * substituteQuantity     → substituteQuantity   （替代数量因子）
 * substituteUnit         → substituteUnit       （替代单位）
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * BomLinks  1 ── N  BomSubstituteLink  (bomLinkOid → BomLinks.oid)
 * Part      1 ── N  BomSubstituteLink  (sourcePartOid    → Part.oid)
 * Part      1 ── N  BomSubstituteLink  (substitutePartOid → Part.oid)
 * </pre>
 *
 * <h3>替代类型（substituteType）</h3>
 * <pre>
 * EQUIVALENT   等效替代 —— 功能、性能完全一致，可无条件互换
 * COMPLETE     完全替代 —— 替代件完全覆盖原子部件用途
 * PARTIAL      部分替代 —— 仅在特定场景/条件下可替代
 * SUBSTITUTE   临时替代 —— 原子部件缺货时的临时替代方案
 * </pre>
 *
 * <h3>全局替代 vs BOM 行替代</h3>
 * <pre>
 * 全局替代（PartAlternateLink）：物料主数据级，对称双向，任意 BOM 中均可引用
 * BOM 行替代（BomSubstituteLink）：BOM 行级，单向，仅在该 BOM 行上下文生效
 * </pre>
 */
public class BomSubstituteLink extends WithoutVersionEntity implements TenantEntity {

    /** 父 BOM 行 oid（关联 ck_bom_links.oid，局部替代挂载点） */
    private String bomLinkOid;

    /** 原子部件主对象 oid（被替代的子部件，关联 ck_part.oid） */
    private String sourcePartOid;

    /** 替代件主对象 oid（关联 ck_part.oid） */
    private String substitutePartOid;

    /** 替代类型：EQUIVALENT / COMPLETE / PARTIAL / SUBSTITUTE */
    private String substituteType;

    /** 替代数量因子（如 1 个原子部件 = N 个替代件，默认 1:1） */
    private Double substituteQuantity;

    /** 替代单位 */
    private String substituteUnit;

    /** 优先级（同 BOM 行多个替代件时的优先顺序，越小越优先） */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 生效性配置 JSONB（预留日期/批次/序列号生效性） */
    private String effectivityJson;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public BomSubstituteLink() {
        super();
        this.substituteQuantity = 1.0;
        this.enabled = true;
    }

    // ==================== Getter / Setter ====================

    public String getBomLinkOid() { return bomLinkOid; }
    public void setBomLinkOid(String bomLinkOid) { this.bomLinkOid = bomLinkOid; }

    public String getSourcePartOid() { return sourcePartOid; }
    public void setSourcePartOid(String sourcePartOid) { this.sourcePartOid = sourcePartOid; }

    public String getSubstitutePartOid() { return substitutePartOid; }
    public void setSubstitutePartOid(String substitutePartOid) { this.substitutePartOid = substitutePartOid; }

    public String getSubstituteType() { return substituteType; }
    public void setSubstituteType(String substituteType) { this.substituteType = substituteType; }

    public Double getSubstituteQuantity() { return substituteQuantity; }
    public void setSubstituteQuantity(Double substituteQuantity) { this.substituteQuantity = substituteQuantity; }

    public String getSubstituteUnit() { return substituteUnit; }
    public void setSubstituteUnit(String substituteUnit) { this.substituteUnit = substituteUnit; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getEffectivityJson() { return effectivityJson; }
    public void setEffectivityJson(String effectivityJson) { this.effectivityJson = effectivityJson; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 判断替代关系是否启用 */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** 判断是否为等效替代（可无条件互换） */
    public boolean isEquivalent() {
        return "EQUIVALENT".equalsIgnoreCase(substituteType);
    }

    @Override
    public String toString() {
        return "BomSubstituteLink{bomLinkOid='" + bomLinkOid
                + "', sourcePartOid='" + sourcePartOid
                + "', substitutePartOid='" + substitutePartOid
                + "', substituteType='" + substituteType
                + "', substituteQuantity=" + substituteQuantity
                + ", substituteUnit='" + substituteUnit
                + "', priority=" + priority
                + ", enabled=" + enabled + "}";
    }
}
