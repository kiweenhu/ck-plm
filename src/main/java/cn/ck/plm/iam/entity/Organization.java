/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

import java.util.List;

/**
 * 组织架构实体，支持树形层级结构。
 *
 * <p>通过 {@code parentOid} 自引用实现父子组织关系，
 * 根组织 {@code parentOid} 为 null。
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * {@code code} 为业务唯一键，<b>禁止定义 id 字段</b>。
 */
public class Organization extends cn.ck.plm.base.entity.BaseEntity implements cn.ck.plm.base.entity.TenantEntity {

    /** 组织编码（业务唯一键） */
    private String code;

    /** 组织名称 */
    private String name;

    /** 父组织 oid（null 表示根组织） */
    private String parentOid;

    /** 组织描述 */
    private String description;

    /** 是否启用 */
    private boolean enabled = true;

    /** 子组织列表（仅用于树形查询，不持久化） */
    private List<Organization> children;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Organization() {
    }

    public Organization(String code, String name, String parentOid) {
        this.code = code;
        this.name = name;
        this.parentOid = parentOid;
    }

    // ==================== Getter / Setter ====================

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentOid() {
        return parentOid;
    }

    public void setParentOid(String parentOid) {
        this.parentOid = parentOid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Organization> getChildren() {
        return children;
    }

    public void setChildren(List<Organization> children) {
        this.children = children;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Organization that = (Organization) o;
        return code != null && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Organization{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", parentOid='" + parentOid + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
