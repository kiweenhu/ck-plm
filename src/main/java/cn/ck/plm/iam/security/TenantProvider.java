/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 租户解析策略 —— 定义如何从 HTTP 请求中提取租户 oid。
 *
 * <p><b>支持多种策略，按优先级尝试：</b>
 * <ol>
 *   <li>JWT Claim 或用户属性: 从已认证用户的组织信息推断租户（推荐）</li>
 *   <li>请求头: {@code X-Tenant-Oid} — 调试/API 调用</li>
 *   <li>子域名: {@code t001.ckplm.com} — SaaS 多租户门户</li>
 *   <li>路径参数: {@code /api/t/{tenantOid}/documents} — RESTful 风格</li>
 * </ol>
 */
public interface TenantProvider {

    /**
     * 从请求中解析租户 oid。
     *
     * @param request HTTP 请求
     * @return tenantOid，若无法解析返回 null（视为默认租户）
     */
    String resolveTenantOid(HttpServletRequest request);
}
