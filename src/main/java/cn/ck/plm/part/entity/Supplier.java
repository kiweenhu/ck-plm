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
 * 供应商主数据（Supplier / Vendor），参考 Windchill 的 Vendor 对象。
 *
 * <p>MPN 管理三层结构的第三层「从哪买」：AVL（{@link PartAvl}）的引用目标。
 * 与制造商（{@link Manufacturer}）是<b>两个独立对象</b>——供应商代理别人的料，
 * 制造商被代理；两者混成一张表会让"同一料多家供货"与"同一供应商多家原厂"都无法表达。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Vendor（Approved Vendor List 的引用端） → Supplier
 * contactName / phone / email              → contact / phone / email
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Supplier  1 ── N  PartAvl   (supplierOid → Supplier.oid)
 * </pre>
 *
 * <p>不版本化：供应商名录是企业级字典数据（注册信息变了走审计留痕）。
 */
public class Supplier extends WithoutVersionEntity implements TenantEntity {

    /** 供应商全称（合同主体；name 为内部简称） */
    private String fullName;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 联系邮箱 */
    private String email;

    /** 官网 */
    private String website;

    /** 备注 */
    private String note;

    /** 是否停用（停用而非删除：已被 AVL 引用的供应商不能消失） */
    private Boolean disabled;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Supplier() {
        super();
        this.disabled = false;
        this.sortOrder = 0;
    }

    // ==================== Getter / Setter ====================

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

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

    /** 是否可用（未停用）。命名理由同 {@link Manufacturer#isEffectivelyEnabled()} */
    public boolean isEffectivelyEnabled() {
        return disabled == null || !disabled;
    }

    @Override
    public String toString() {
        return "Supplier{code='" + getCode() + "', name='" + getName()
                + "', fullName='" + fullName + "', contact='" + contact
                + "', disabled=" + disabled + "}";
    }
}
