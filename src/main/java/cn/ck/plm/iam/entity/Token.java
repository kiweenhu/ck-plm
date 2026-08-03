/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

import cn.ck.plm.base.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 用户认证 Token 实体 —— 持久化到 ck_token 表。
 *
 * <p>每次登录生成一条 token 记录，3 天自动过期。
 * Token 字符串本身仍保存在前端 localStorage 中，每次请求通过
 * Authorization 头传给后端校验。
 *
 * <p>token 中关联了租户信息（tenantId/tenantName），
 * 避免每次校验时都要查用户表和租户表。
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * {@code token} 为唯一键（UUID v4 字符串）。
 */
public class Token extends BaseEntity {

    /** Token 值（UUID v4） */
    private String token;

    /** 所属用户名 */
    private String username;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 租户 oid（引用 ck_tenant.oid，缓存用） */
    private String tenantOid;

    /** 租户名称 */
    private String tenantName;

    // ==================== 构造方法 ====================

    public Token() {
    }

    public Token(String token, String username, LocalDateTime expireAt) {
        this.token = token;
        this.username = username;
        this.expireAt = expireAt;
    }

    // ==================== Getter / Setter ====================

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public String getTenantOid() {
        return tenantOid;
    }

    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    // ==================== 便捷方法 ====================

    /** 是否已过期 */
    public boolean isExpired() {
        return expireAt != null && expireAt.isBefore(LocalDateTime.now());
    }
}
