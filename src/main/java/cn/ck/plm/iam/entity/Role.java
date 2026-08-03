/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

/**
 * 角色实体，用于权限控制。
 *
 * <p>角色通过 {@code ck_role_member} 关联表与 {@link User} 建立多对多关系。
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * {@code code} 为角色编码唯一键，<b>禁止定义 id 字段</b>。
 *
 * <h3>角色类型</h3>
 * <ul>
 *   <li>{@code PLATFORM} — 平台级角色（系统管理员、审计管理员、安全管理员），系统初始化时导入，不可编辑/删除</li>
 *   <li>{@code BUSINESS} — 自定义业务角色/岗位，由管理员自由维护</li>
 * </ul>
 */
public class Role extends cn.ck.plm.base.entity.BaseEntity implements cn.ck.plm.base.entity.TenantEntity {

    /** 角色编码（业务唯一键，如 ADMIN / DESIGNER / VIEWER） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 角色类型：PLATFORM（平台级，不可删除/编辑）| BUSINESS（自定义业务流程角色） */
    private String roleType;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Role() {
    }

    public Role(String code, String name) {
        this.code = code;
        this.name = name;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    /** 是否为平台级角色（不可编辑/删除） */
    public boolean isPlatform() {
        return "PLATFORM".equals(roleType);
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
        Role role = (Role) o;
        return code != null && code.equals(role.code);
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Role{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", roleType='" + roleType + '\'' +
                '}';
    }
}
