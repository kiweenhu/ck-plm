/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

/**
 * 制造商主数据（Manufacturer），参考 Windchill 的 Manufacturer 对象。
 *
 * <p>它是 MPN 管理三层结构的第一层「谁造的」：一家制造商提供多个制造商料号（MPN），
 * 多个制造商料号又可以挂到同一个内部件上（见 {@link PartAml}）。与供应商
 * （{@link Supplier}）是<b>两个独立对象</b>——一家供应商会代理多家制造商的料，
 * 同一料也会由多家供应商供货，这正是"多货源"与"单一来源风险"的判定基础。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Manufacturer           → Manufacturer
 * name                   → name / englishName（英文名常与原厂资料一致，故单独留一列）
 * （无对应）              → country（原产地，供应风险判断的常见维度）
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Manufacturer  1 ── N  PartAml   (manufacturerOid → Manufacturer.oid)
 * </pre>
 *
 * <p>不版本化（继承 {@link WithoutVersionEntity}）：制造商名录是企业级字典数据，
 * 改名走审计留痕即可，不需要 A/B 版并存。
 */
public class Manufacturer extends WithoutVersionEntity implements TenantEntity {

    /** 英文名（与原厂资料一致，如 Texas Instruments） */
    private String englishName;

    /** 官网 */
    private String website;

    /** 原产地 / 注册地 */
    private String country;

    /** 备注 */
    private String note;

    /** 是否停用（停用而非删除：已被 AML 引用的制造商不能消失） */
    private Boolean disabled;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Manufacturer() {
        super();
        this.disabled = false;
        this.sortOrder = 0;
    }

    // ==================== Getter / Setter ====================

    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /**
     * 是否可用（未停用）。
     * <p>刻意不命名为 {@code isDisabled()}：与 {@link #getDisabled()}（Boolean）
     * 构成 JavaBeans 规范中的模糊重载 getter，MyBatis 反射会报
     * {@code Illegal overloaded getter method}（同 PartAlternateLink 的踩坑注释）。
     */
    public boolean isEffectivelyEnabled() {
        return disabled == null || !disabled;
    }

    @Override
    public String toString() {
        return "Manufacturer{code='" + getCode() + "', name='" + getName()
                + "', englishName='" + englishName + "', country='" + country
                + "', disabled=" + disabled + "}";
    }
}
