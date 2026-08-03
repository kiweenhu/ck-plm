/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

/**
 * 角色成员关联实体，实现多对多关系。
 *
 * <p>通过 {@code userOid} 和 {@code roleOid} 分别关联 {@link User} 和 {@link Role}，
 * 联合唯一约束防止重复授权。
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，<b>禁止定义 id 字段</b>。
 */
public class RoleMember extends cn.ck.plm.base.entity.BaseEntity implements cn.ck.plm.base.entity.TenantEntity {

    /** 用户 oid */
    private String userOid;

    /** 角色 oid */
    private String roleOid;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public RoleMember() {
    }

    public RoleMember(String userOid, String roleOid) {
        this.userOid = userOid;
        this.roleOid = roleOid;
    }

    // ==================== Getter / Setter ====================

    public String getUserOid() {
        return userOid;
    }

    public void setUserOid(String userOid) {
        this.userOid = userOid;
    }

    public String getRoleOid() {
        return roleOid;
    }

    public void setRoleOid(String roleOid) {
        this.roleOid = roleOid;
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
        RoleMember roleMember = (RoleMember) o;
        return userOid != null && userOid.equals(roleMember.userOid)
                && roleOid != null && roleOid.equals(roleMember.roleOid);
    }

    @Override
    public int hashCode() {
        int result = userOid != null ? userOid.hashCode() : 0;
        result = 31 * result + (roleOid != null ? roleOid.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RoleMember{" +
                "userOid='" + userOid + '\'' +
                ", roleOid='" + roleOid + '\'' +
                '}';
    }
}
