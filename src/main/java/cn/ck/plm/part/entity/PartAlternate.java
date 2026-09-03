/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.WithoutVersionEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 部件替代关系（Part Alternate），参考 Windchill WTPartAlternateLink 模型。
 *
 * <p>描述一个部件可以被另一个部件替代的全局替代关系。与 BOM 行（{@link cn.ck.plm.bom.entity.BomLinks}）
 * 不同，替代关系是<b>全局</b>的：不限定于某个特定 BOM 结构，只要建立了替代关系，
 * 在任意 BOM 中遇到源部件时，均可按此关系给出可选的替代部件。
 *
 * <p>替代关系挂在部件主对象级别（而非迭代级别），继承 {@link WithoutVersionEntity}，
 * 不需要自身的版本控制。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * WTPartAlternateLink → PartAlternate
 * roleAObjectReference → sourcePartOid      （原部件，被替代方）
 * roleBObjectReference → alternatePartOid   （替代部件）
 * alternateNumber      → code               （替代关系编码，继承自 WithoutVersionEntity）
 * alternateQuantity    → alternateQuantity  （替代数量）
 * alternateUnit        → alternateUnit      （替代单位）
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Part  1 ── N  PartAlternate  (sourcePartOid    → Part.oid，原部件)
 * Part  1 ── N  PartAlternate  (alternatePartOid → Part.oid，替代部件)
 * </pre>
 *
 * <h3>替代类型（alternateType）</h3>
 * <pre>
 * EQUIVALENT   等效替代 —— 功能、性能完全一致，可无条件互换
 * COMPLETE     完全替代 —— 替代部件完全覆盖原部件用途
 * PARTIAL      部分替代 —— 仅在特定场景/条件下可替代
 * SUBSTITUTE   临时替代 —— 原部件缺货时的临时替代方案
 * </pre>
 */
public class PartAlternate extends WithoutVersionEntity implements TenantEntity {

    /** 原部件主对象 oid（被替代的部件，关联 ck_part.oid） */
    private String sourcePartOid;

    /** 替代部件主对象 oid（关联 ck_part.oid） */
    private String alternatePartOid;

    /** 替代类型：EQUIVALENT / COMPLETE / PARTIAL / SUBSTITUTE */
    private String alternateType;

    /** 替代数量（如 1 个原部件 = N 个替代部件，默认 1:1） */
    private Double alternateQuantity;

    /** 替代单位 */
    private String alternateUnit;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartAlternate() {
        super();
        this.alternateQuantity = 1.0;
        this.enabled = true;
    }

    // ==================== Getter / Setter ====================

    public String getSourcePartOid() { return sourcePartOid; }
    public void setSourcePartOid(String sourcePartOid) { this.sourcePartOid = sourcePartOid; }

    public String getAlternatePartOid() { return alternatePartOid; }
    public void setAlternatePartOid(String alternatePartOid) { this.alternatePartOid = alternatePartOid; }

    public String getAlternateType() { return alternateType; }
    public void setAlternateType(String alternateType) { this.alternateType = alternateType; }

    public Double getAlternateQuantity() { return alternateQuantity; }
    public void setAlternateQuantity(Double alternateQuantity) { this.alternateQuantity = alternateQuantity; }

    public String getAlternateUnit() { return alternateUnit; }
    public void setAlternateUnit(String alternateUnit) { this.alternateUnit = alternateUnit; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

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
        return "EQUIVALENT".equalsIgnoreCase(alternateType);
    }

    @Override
    public String toString() {
        return "PartAlternate{sourcePartOid='" + sourcePartOid
                + "', alternatePartOid='" + alternatePartOid
                + "', alternateType='" + alternateType
                + "', alternateQuantity=" + alternateQuantity
                + ", alternateUnit='" + alternateUnit
                + "', enabled=" + enabled + "}";
    }
}
