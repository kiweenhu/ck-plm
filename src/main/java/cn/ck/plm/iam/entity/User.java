/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

/**
 * 用户实体，用于登录认证和权限管理。
 *
 * <p>用户属于某个组织（{@code orgOid}），通过 {@code ck_role_member} 关联表
 * 与 {@link Role} 建立多对多关系。
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * {@code username} 为登录唯一键，<b>禁止定义 id 字段</b>。
 */
public class User extends cn.ck.plm.base.entity.BaseEntity implements cn.ck.plm.base.entity.TenantEntity {

    /** 登录用户名（唯一） */
    private String username;

    /** 加密后的密码 */
    private String password;

    /** 显示名称（姓名） */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 电话 */
    private String phone;

    /** 所属组织 oid */
    private String orgOid;

    /** 是否启用 */
    private boolean enabled = true;

    /** 是否锁定 */
    private boolean locked;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public User() {
    }

    public User(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    // ==================== Getter / Setter ====================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOrgOid() {
        return orgOid;
    }

    public void setOrgOid(String orgOid) {
        this.orgOid = orgOid;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
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
        User user = (User) o;
        return username != null && username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", enabled=" + enabled +
                ", locked=" + locked +
                '}';
    }
}
