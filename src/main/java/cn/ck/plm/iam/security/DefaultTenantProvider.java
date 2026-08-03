/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.base.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认租户解析器 —— Header 优先，用户组织兜底。
 *
 * <p>解析优先级：
 * <ol>
 *   <li>请求头 {@code X-Tenant-Oid} — 最高优先级（调试/API）</li>
 *   <li>子域名: {@code t001.ckplm.com → t001} — SaaS 场景</li>
 *   <li>用户所属组织推断 — 登录用户默认所属租户</li>
 *   <li>默认值 — 默认租户 oid</li>
 * </ol>
 */
@Component
public class DefaultTenantProvider implements TenantProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantProvider.class);

    private static final String HEADER_TENANT = "X-Tenant-Oid";

    private final UserService userService;

    public DefaultTenantProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public String resolveTenantOid(HttpServletRequest request) {
        // 方案1：从请求头获取（调试/API）
        String tenantOid = request.getHeader(HEADER_TENANT);
        if (tenantOid != null && !tenantOid.isEmpty()) {
            log.debug("租户解析: Header X-Tenant-Oid = {}", tenantOid);
            return tenantOid;
        }

        // 方案2：从子域名解析（SaaS 场景）
        // tenantOid = resolveFromSubdomain(request.getServerName());
        // if (tenantOid != null) return tenantOid;

        // 方案3：从已认证用户推断（默认策略）
        String username = UserContext.get();
        if (username != null && !"system".equals(username)) {
            try {
                User user = userService.findByUsername(username);
                if (user != null && user.getTenantOid() != null) {
                    log.debug("租户解析: 用户 {} → tenantOid = {}", username, user.getTenantOid());
                    return user.getTenantOid();
                }
            } catch (Exception e) {
                log.warn("租户解析失败: username={}, error={}", username, e.getMessage());
            }
        }

        // 方案4：默认租户 oid
        log.debug("租户解析: 使用默认租户 oid");
        return TenantContext.DEFAULT_TENANT_OID;
    }
}
