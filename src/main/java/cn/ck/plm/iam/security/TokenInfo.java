/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

/**
 * Token 校验结果 —— 包含用户和租户信息。
 */
public class TokenInfo {

    private final String username;
    private final String tenantOid;
    private final String tenantName;

    public TokenInfo(String username, String tenantOid, String tenantName) {
        this.username = username;
        this.tenantOid = tenantOid;
        this.tenantName = tenantName;
    }

    public String getUsername() {
        return username;
    }

    /** 获取租户 oid（引用 ck_tenant.oid） */
    public String getTenantOid() {
        return tenantOid;
    }

    public String getTenantName() {
        return tenantName;
    }

    public static TokenInfo of(String username, String tenantOid, String tenantName) {
        return new TokenInfo(username, tenantOid, tenantName);
    }
}
