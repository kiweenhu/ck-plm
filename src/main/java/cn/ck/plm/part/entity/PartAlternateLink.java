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
 * 部件双向替代关系（Part Alternate Link），参考 Windchill WTPartAlternateLink 模型。
 *
 * <p>描述一个部件可以被另一个部件替代的<b>全局双向替代</b>关系。与 BOM 行替代
 * （{@link cn.ck.plm.bom.entity.BomSubstituteLink}）不同，双向替代是全局的：
 * 不限定于某个特定 BOM 结构，只要建立了替代关系，在任意 BOM 中遇到任一部件时，
 * 均可按此关系给出可互换的另一部件。
 *
 * <p>替代关系挂在部件主数据（Master）级别（而非迭代级别），继承 {@link WithoutVersionEntity}，
 * 不需要自身的版本控制。A 物料升版，互换关系纹丝不动，无需重建——这是全局语义
 * 与局部语义的分水岭。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * WTPartAlternateLink    → PartAlternateLink
 * roleAObjectReference   → roleAPartOid       （roleA 角色端，部件主数据）
 * roleBObjectReference   → roleBPartOid       （roleB 角色端，部件主数据）
 * alternateNumber        → code               （替代关系编码，继承自 WithoutVersionEntity）
 * alternateQuantity      → alternateQuantity  （替代数量因子）
 * alternateUnit          → alternateUnit      （替代单位）
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Part  1 ── N  PartAlternateLink  (roleAPartOid → Part.oid)
 * Part  1 ── N  PartAlternateLink  (roleBPartOid → Part.oid)
 * </pre>
 *
 * <h3>对称双向存储</h3>
 * <p>双向替代是<b>对称</b>关系（A 可换 B 等价于 B 可换 A），因此采用一条 link、
 * 两个角色端（roleA / roleB）建模，数据库层通过<b>有序对</b>唯一约束防重复
 * （{@code (role_a_part_oid, role_b_part_oid)} 唯一，且约定 role_a &lt; role_b），
 * 避免"两条记录表达一个事实"导致的数据不一致。
 *
 * <h3>替代类型（alternateType）</h3>
 * <pre>
 * EQUIVALENT   等效替代 —— 功能、性能完全一致，可无条件互换
 * COMPLETE     完全替代 —— 替代部件完全覆盖原部件用途
 * PARTIAL      部分替代 —— 仅在特定场景/条件下可替代
 * SUBSTITUTE   临时替代 —— 原部件缺货时的临时替代方案
 * </pre>
 */
public class PartAlternateLink extends WithoutVersionEntity implements TenantEntity {

    /** roleA 角色端部件主数据 oid（关联 ck_part.oid，有序对中较小的一方） */
    private String roleAPartOid;

    /** roleB 角色端部件主数据 oid（关联 ck_part.oid，有序对中较大的一方） */
    private String roleBPartOid;

    /** 替代类型：EQUIVALENT / COMPLETE / PARTIAL / SUBSTITUTE */
    private String alternateType;

    /** 替代数量因子（如 1 个 A = N 个 B，默认 1:1） */
    private Double alternateQuantity;

    /** 替代单位 */
    private String alternateUnit;

    /** 是否启用 */
    private Boolean enabled;

    /** 生效性配置 JSONB（预留日期/批次/序列号生效性） */
    private String effectivityJson;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartAlternateLink() {
        super();
        this.alternateQuantity = 1.0;
        this.enabled = true;
    }

    // ==================== Getter / Setter ====================

    public String getRoleAPartOid() { return roleAPartOid; }
    public void setRoleAPartOid(String roleAPartOid) { this.roleAPartOid = roleAPartOid; }

    public String getRoleBPartOid() { return roleBPartOid; }
    public void setRoleBPartOid(String roleBPartOid) { this.roleBPartOid = roleBPartOid; }

    public String getAlternateType() { return alternateType; }
    public void setAlternateType(String alternateType) { this.alternateType = alternateType; }

    public Double getAlternateQuantity() { return alternateQuantity; }
    public void setAlternateQuantity(Double alternateQuantity) { this.alternateQuantity = alternateQuantity; }

    public String getAlternateUnit() { return alternateUnit; }
    public void setAlternateUnit(String alternateUnit) { this.alternateUnit = alternateUnit; }

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
        return "EQUIVALENT".equalsIgnoreCase(alternateType);
    }

    @Override
    public String toString() {
        return "PartAlternateLink{roleAPartOid='" + roleAPartOid
                + "', roleBPartOid='" + roleBPartOid
                + "', alternateType='" + alternateType
                + "', alternateQuantity=" + alternateQuantity
                + ", alternateUnit='" + alternateUnit
                + "', enabled=" + enabled + "}";
    }
}
