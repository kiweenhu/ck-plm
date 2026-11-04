/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.base.entity.UserActivity;
import cn.ck.plm.base.mapper.UserActivityMapper;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.dto.LoginRequest;
import cn.ck.plm.iam.dto.LoginResponse;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.security.TokenInfo;
import cn.ck.plm.iam.security.TokenStore;
import cn.ck.plm.iam.service.api.TenantService;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.iam.util.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证控制器 —— 处理登录 / 登出。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final TokenStore tokenStore;
    private final UserActivityMapper activityMapper;
    private final TenantService tenantService;

    public AuthController(UserService userService, TokenStore tokenStore,
                          UserActivityMapper activityMapper, TenantService tenantService) {
        this.userService = userService;
        this.tokenStore = tokenStore;
        this.activityMapper = activityMapper;
        this.tenantService = tenantService;
    }

    /**
     * 登录：验证用户名密码，返回 token 及用户信息。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        long startMs = System.currentTimeMillis();
        if (request.getUsername() == null || request.getPassword() == null) {
            return ApiResponse.fail(400, "用户名和密码不能为空");
        }

        User user = userService.findByUsername(request.getUsername().trim());
        if (user == null) {
            return ApiResponse.fail(401, "用户名或密码错误");
        }

        // 账号自身不可用时，先把"企业侧"的原因摆上来。
        // 注册租户时管理员账号就是按 enabled=false + locked=true 建的（审核通过前不可登录，
        // 见 TenantServiceImpl.register；审核通过时由 approve 打开）—— 所以这两面旗子常常只是
        // "企业还没过审"的表象。若不让租户状态先说话，用户看到的永远是"用户已被禁用"，
        // 转头去查自己的账号，方向全错。
        if (!user.isEnabled() || user.isLocked()) {
            String tenantBlock = describeTenantBlock(user);
            if (tenantBlock != null) {
                return ApiResponse.fail(403, tenantBlock);
            }
            return ApiResponse.fail(403, user.isEnabled() ? "用户已被锁定" : "用户已被禁用");
        }

        if (!PasswordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResponse.fail(401, "用户名或密码错误");
        }

        // 查询租户名称，同时校验租户状态
        String tenantName = null;
        if (user.getTenantOid() != null) {
            Tenant tenant = tenantService.getByOid(user.getTenantOid());
            if (tenant == null) {
                return ApiResponse.fail(403, "租户信息不存在");
            }
            if (!tenant.isActive()) {
                // 同样是"企业侧"的原因，按真实状态说 —— 原先一律说"等待管理员审核"，
                // 对被驳回 / 已暂停 / 已停用的租户都不成立
                return ApiResponse.fail(403, tenantStatusMessage(tenant));
            }
            tenantName = tenant.getName();
        }
        String token = tokenStore.create(user.getUsername(), user.getTenantOid(), tenantName);

        List<Role> roles = userService.getUserRoles(user.getOid());
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getDisplayName());
        resp.setOid(user.getOid());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setOrgOid(user.getOrgOid());
        resp.setRoles(roleCodes);
        resp.setTenantOid(user.getTenantOid());

        // 登录成功 → 记录日志
        try {
            UserActivity log = new UserActivity();
            log.setOid(UUID.randomUUID().toString());
            log.setUserOid(user.getOid());
            log.setActivityType("LOGIN");
            log.setActionDesc("用户登录");
            log.setTargetName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
            log.setTargetType("系统");
            log.setResult("SUCCESS");
            log.setDurationMs((int)(System.currentTimeMillis() - startMs));
            log.setOperatorIp(getClientIp(httpRequest));
            log.setUserAgent(httpRequest.getHeader("User-Agent"));
            log.setCreator(user.getUsername());
            log.setUpdater(user.getUsername());
            activityMapper.insert(log);
        } catch (Exception ignored) { }

        return ApiResponse.ok(resp);
    }

    /**
     * 验证 token 是否有效 —— 用于前端路由守卫自动校验 localStorage 中的 token。
     * 不拦截，不校验拦截器，token 有效则返回用户信息，无效返回 401。
     */
    @GetMapping("/verify")
    public ApiResponse<LoginResponse> verify(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = resolveCurrentUser(authHeader);
        if (user == null) {
            return ApiResponse.fail(401, "token 无效或已过期");
        }
        List<Role> roles = userService.getUserRoles(user.getOid());
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());

        LoginResponse resp = new LoginResponse();
        resp.setToken(authHeader.substring(7).trim());
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getDisplayName());
        resp.setOid(user.getOid());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setOrgOid(user.getOrgOid());
        resp.setRoles(roleCodes);
        resp.setTenantOid(user.getTenantOid());
        return ApiResponse.ok(resp);
    }

    /**
     * 登出：使 token 失效。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader, HttpServletRequest httpRequest) {
        String currentUser = UserContext.get();
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenStore.remove(authHeader.substring(7).trim());
        }
        // 记录注销日志
        try {
            UserActivity log = new UserActivity();
            log.setOid(UUID.randomUUID().toString());
            log.setUserOid(currentUser != null ? currentUser : "");
            log.setActivityType("LOGOUT");
            log.setActionDesc("用户注销");
            log.setTargetName(currentUser != null ? currentUser : "");
            log.setTargetType("系统");
            log.setResult("SUCCESS");
            log.setOperatorIp(getClientIp(httpRequest));
            log.setUserAgent(httpRequest.getHeader("User-Agent"));
            log.setCreator(currentUser);
            log.setUpdater(currentUser);
            activityMapper.insert(log);
        } catch (Exception ignored) { }
        return ApiResponse.ok();
    }

    /**
     * 获取当前登录用户的完整信息。
     */
    @GetMapping("/me")
    public ApiResponse<User> me(@RequestHeader("Authorization") String authHeader) {
        User user = resolveCurrentUser(authHeader);
        if (user == null) {
            return ApiResponse.fail(401, "未登录或 token 已失效");
        }
        // 不暴露密码
        user.setPassword(null);
        return ApiResponse.ok(user);
    }

    /**
     * 更新当前用户的个人信息（displayName / email / phone）。
     */
    @PutMapping("/profile")
    public ApiResponse<User> updateProfile(@RequestHeader("Authorization") String authHeader,
                                           @RequestBody Map<String, Object> body) {
        User current = resolveCurrentUser(authHeader);
        if (current == null) {
            return ApiResponse.fail(401, "未登录或 token 已失效");
        }
        User update = new User();
        update.setOid(current.getOid());
        if (body.containsKey("displayName")) {
            update.setDisplayName((String) body.get("displayName"));
        }
        if (body.containsKey("email")) {
            update.setEmail((String) body.get("email"));
        }
        if (body.containsKey("phone")) {
            update.setPhone((String) body.get("phone"));
        }
        try {
            User result = userService.updateProfile(update);
            result.setPassword(null);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 当前用户修改密码。
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody Map<String, String> body) {
        User current = resolveCurrentUser(authHeader);
        if (current == null) {
            return ApiResponse.fail(401, "未登录或 token 已失效");
        }
        try {
            userService.changePassword(current.getOid(),
                    body.get("oldPassword"), body.get("newPassword"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 退出其他设备：保留当前会话，其余 token 全部失效。
     *
     * <p>密码疑似泄露、或在别人电脑上登录过之后的处置手段 —— 自己这台机器不掉线。
     */
    @PostMapping("/logout-others")
    public ApiResponse<Map<String, Integer>> logoutOthers(@RequestHeader("Authorization") String authHeader,
                                                         HttpServletRequest httpRequest) {
        User current = resolveCurrentUser(authHeader);
        if (current == null) {
            return ApiResponse.fail(401, "未登录或 token 已失效");
        }
        int removed = tokenStore.removeOthers(current.getUsername(), authHeader.substring(7).trim());

        // 与注销同一套审计口径：操作日志里能回溯"谁在什么时候把其他设备踢下线了"。
        // 注：ck_user_activity.user_oid 这一列现存数据放的是「用户名」（见 /logout 的写法），
        //     这里必须跟着来，否则「最近操作」按当前登录人查会漏掉本条。
        try {
            String currentUser = UserContext.get();
            UserActivity log = new UserActivity();
            log.setOid(UUID.randomUUID().toString());
            log.setUserOid(currentUser != null ? currentUser : current.getUsername());
            log.setActivityType("LOGOUT");
            log.setActionDesc("退出其他设备");
            log.setTargetName(currentUser != null ? currentUser : current.getUsername());
            log.setTargetType("系统");
            log.setResult("SUCCESS");
            log.setOperatorIp(getClientIp(httpRequest));
            log.setUserAgent(httpRequest.getHeader("User-Agent"));
            log.setCreator(current.getUsername());
            log.setUpdater(current.getUsername());
            activityMapper.insert(log);
        } catch (Exception ignored) { }

        return ApiResponse.ok(Map.of("removed", removed));
    }

    /**
     * 用户被禁用/锁定时，先看它所属租户是不是卡在准入环节。
     *
     * <p>这里最容易把人带偏：注册时管理员账号就是 {@code enabled=false + locked=true}
     * （见 {@code TenantServiceImpl.register}），由 {@code approve} 在审核通过时打开 ——
     * 于是"用户已被禁用"常常只是"企业还没过审"的表象。不先问租户，用户就会去查自己的账号。
     *
     * @return 租户侧的说明；租户正常（ACTIVE）或无租户（平台级账号）时返回 {@code null}
     */
    private String describeTenantBlock(User user) {
        if (user.getTenantOid() == null) {
            return null;
        }
        Tenant tenant = tenantService.getByOid(user.getTenantOid());
        if (tenant == null) {
            return "租户信息不存在";
        }
        return tenant.isActive() ? null : tenantStatusMessage(tenant);
    }

    /** 租户状态 → 给用户看的说明（把"为什么登不进去"说准，而不是一律"等审核"） */
    private String tenantStatusMessage(Tenant tenant) {
        String status = tenant.getStatus();
        if (Tenant.STATUS_PENDING.equals(status)) {
            return "您的企业申请正在平台审核中，审核通过后即可登录";
        }
        if (Tenant.STATUS_REJECTED.equals(status)) {
            String reason = tenant.getRejectReason();
            return (reason == null || reason.trim().isEmpty())
                    ? "您的企业申请未通过审核，请联系平台管理员"
                    : "您的企业申请未通过审核：" + reason;
        }
        if (Tenant.STATUS_SUSPENDED.equals(status)) {
            return "您的企业已被暂停使用，请联系平台管理员";
        }
        if (Tenant.STATUS_DISABLED.equals(status)) {
            return "您的企业已被停用，请联系平台管理员";
        }
        return "租户状态异常（" + status + "），请联系平台管理员";
    }

    /**
     * 从 Authorization 头解析当前用户。
     */
    private User resolveCurrentUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        TokenInfo tokenInfo = tokenStore.validate(token);
        if (tokenInfo == null) {
            return null;
        }
        return userService.findByUsername(tokenInfo.getUsername());
    }

    /** 获取客户端真实 IP */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
