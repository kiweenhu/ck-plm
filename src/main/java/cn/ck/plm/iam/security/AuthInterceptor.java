/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 认证拦截器 —— 校验请求中的 Authorization Token，设置 {@link UserContext} 和 {@link TenantContext}。
 *
 * <p>从请求头 {@code Authorization: Bearer <token>} 中提取 token，
 * 通过 {@link TokenStore} 验证后将 username 写入 {@link UserContext}，
 * 同时通过 {@link TenantProvider} 解析租户标识写入 {@link TenantContext}。
 *
 * <p>白名单路径（无需认证）在 {@link AuthConfig} 中配置。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final String HEADER_AUTH = "Authorization";
    private static final String PREFIX_BEARER = "Bearer ";

    private final TokenStore tokenStore;
    private final TenantProvider tenantProvider;

    public AuthInterceptor(TokenStore tokenStore, TenantProvider tenantProvider) {
        this.tokenStore = tokenStore;
        this.tenantProvider = tenantProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ckfiles / attachments 的读取（在线预览、下载、GLB 数据流）由 &lt;img&gt;、window.open、
        // three.js 加载器直接发起，带不上 Authorization 头 —— 这类 GET 放行（与排除名单原本的意图一致）。
        // 写操作（上传 / 建 URL）必须带 token：拿不到用户与租户时文件会落进默认租户，
        // 之后按当前租户查不到该 CKFile，CAD 转换就报"源文件不存在"（2026-10-23 实测踩到）。
        String uri = request.getRequestURI();
        boolean readOnlyFileAccess = ("GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod()))
                && (uri.startsWith("/api/ckfiles/") || uri.startsWith("/api/attachments/"));
        if (readOnlyFileAccess) {
            return true;
        }

        String authHeader = request.getHeader(HEADER_AUTH);
        if (authHeader == null || !authHeader.startsWith(PREFIX_BEARER)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或 token 无效\"}");
            return false;
        }

        String token = authHeader.substring(PREFIX_BEARER.length()).trim();
        TokenInfo tokenInfo = tokenStore.validate(token);
        if (tokenInfo == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token 已过期或无效\"}");
            return false;
        }

        UserContext.set(tokenInfo.getUsername());

        // 优先使用 token 中缓存的租户 oid，无需再查数据库
        String tenantOid = tokenInfo.getTenantOid();
        if (tenantOid == null || tenantOid.isEmpty()) {
            tenantOid = tenantProvider.resolveTenantOid(request);
        }
        TenantContext.set(tenantOid);
        log.debug("TenantContext 已设置: user={}, tenantOid={}", tokenInfo.getUsername(), tenantOid);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
        TenantContext.clear();
    }
}
