/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.dto.TenantRegistrationRequest;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.TenantService;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 租户控制器 —— 注册（公开）+ 审核（平台管理员）。
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private static final Logger log = LoggerFactory.getLogger(TenantController.class);

    /** 平台管理员角色 code */
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final TenantService tenantService;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    public TenantController(TenantService tenantService, UserService userService, JdbcTemplate jdbcTemplate) {
        this.tenantService = tenantService;
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取当前用户所属租户信息 —— 登录用户均可访问。
     */
    @GetMapping("/current")
    public ApiResponse<Tenant> currentTenant() {
        String username = UserContext.get();
        if (username == null) {
            return ApiResponse.fail(401, "未登录");
        }
        try {
            cn.ck.plm.iam.entity.User user = userService.findByUsername(username);
            if (user == null || user.getTenantOid() == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            Tenant tenant = tenantService.getByOid(user.getTenantOid());
            if (tenant == null) {
                return ApiResponse.fail(404, "未找到租户信息");
            }
            // 不返回敏感信息
            tenant.setAdminPassword(null);
            return ApiResponse.ok(tenant);
        } catch (Exception e) {
            log.error("获取当前租户信息失败", e);
            return ApiResponse.fail(500, "获取租户信息失败");
        }
    }

    /**
     * 提交租户注册申请 —— 公开接口。
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody TenantRegistrationRequest request) {
        if (request == null) {
            return ApiResponse.fail(400, "注册信息不能为空");
        }
        try {
            tenantService.register(request);
            log.info("租户注册申请已提交: tenantId={}", request.getTenantId());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("租户注册异常", e);
            return ApiResponse.fail(500, "注册失败，请稍后重试");
        }
    }

    /**
     * 待审核列表 —— 平台管理员接口。
     */
    @GetMapping("/pending")
    public ApiResponse<List<Tenant>> listPending() {
        ApiResponse<Void> permCheck = checkPlatformAdmin();
        if (permCheck != null) return ApiResponse.fail(403, "仅平台管理员可查看待审核租户");

        try {
            List<Tenant> list = tenantService.listPending();
            // 不返回密码
            for (Tenant t : list) {
                t.setAdminPassword(null);
            }
            return ApiResponse.ok(list);
        } catch (Exception e) {
            log.error("查询待审核租户失败", e);
            return ApiResponse.fail(500, "查询失败");
        }
    }

    /** 已激活租户列表 —— 平台管理员接口，供筛选下拉框使用。 */
    @GetMapping("/active")
    public ApiResponse<List<Tenant>> listActive() {
        ApiResponse<Void> permCheck = checkPlatformAdmin();
        if (permCheck != null) return ApiResponse.fail(403, "仅平台管理员可查看租户列表");
        try {
            List<Tenant> list = tenantService.listActive();
            for (Tenant t : list) t.setAdminPassword(null);
            return ApiResponse.ok(list);
        } catch (Exception e) {
            log.error("查询已激活租户失败", e);
            return ApiResponse.fail(500, "查询失败");
        }
    }

    /**
     * 待审核数量 —— 铃铛 Badge 用（平台管理员接口）。
     */
    @GetMapping("/pending/count")
    public ApiResponse<Integer> countPending() {
        ApiResponse<Void> permCheck = checkPlatformAdmin();
        if (permCheck != null) return ApiResponse.fail(403, "仅平台管理员可查看待审核数量");

        return ApiResponse.ok(tenantService.countPending());
    }

    /**
     * 审核通过 —— 平台管理员接口。
     */
    @PutMapping("/{oid}/approve")
    public ApiResponse<Void> approve(@PathVariable String oid) {
        ApiResponse<Void> permCheck = checkPlatformAdmin();
        if (permCheck != null) return ApiResponse.fail(403, "仅平台管理员可审核租户");

        String username = UserContext.get();
        try {
            tenantService.approve(oid, username);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("审核通过失败: oid={}", oid, e);
            return ApiResponse.fail(500, "操作失败");
        }
    }

    /**
     * 驳回申请 —— 平台管理员接口。
     */
    @PutMapping("/{oid}/reject")
    public ApiResponse<Void> reject(@PathVariable String oid, @RequestBody Map<String, String> body) {
        ApiResponse<Void> permCheck = checkPlatformAdmin();
        if (permCheck != null) return ApiResponse.fail(403, "仅平台管理员可驳回租户申请");

        String reason = body != null ? body.getOrDefault("reason", "") : "";
        String username = UserContext.get();
        try {
            tenantService.reject(oid, username, reason);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("驳回失败: oid={}", oid, e);
            return ApiResponse.fail(500, "操作失败");
        }
    }

    /**
     * 校验当前用户是否为平台管理员。
     * 直接用 JDBC 绕过 MyBatis 拦截器，避免 JOIN 查询中 tenant_oid 字段引用不明确。
     */
    private ApiResponse<Void> checkPlatformAdmin() {
        String username = UserContext.get();
        if (username == null) {
            return ApiResponse.fail(401, "未登录");
        }
        try {
            User user = userService.findByUsername(username);
            if (user == null) {
                return ApiResponse.fail(403, "用户不存在");
            }
            // 直接用 JDBC 查询，绕过 TenantStatementInterceptor 对 JOIN 查询的改写问题
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ck_role_member rm " +
                    "INNER JOIN ck_role r ON rm.role_oid = r.oid " +
                    "WHERE rm.user_oid = ? AND r.code = ?",
                    Integer.class, user.getOid(), PLATFORM_ADMIN_ROLE);
            if (count == null || count == 0) {
                log.warn("非平台管理员尝试访问审核接口: user={}", username);
                return ApiResponse.fail(403, "仅平台管理员可执行此操作");
            }
            return null;
        } catch (Exception e) {
            log.error("权限校验异常", e);
            return ApiResponse.fail(500, "权限校验失败");
        }
    }
}
