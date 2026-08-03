/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理 REST 控制器。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 创建用户 */
    @PostMapping
    public ApiResponse<User> create(@RequestBody Map<String, Object> body) {
        try {
            User user = buildUser(body);
            String password = (String) body.get("password");
            return ApiResponse.ok(userService.create(user, password));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新用户（支持部分更新：未传入的字段保留原值） */
    @PutMapping("/{oid}")
    public ApiResponse<User> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            User user = buildUpdateUser(oid, body);
            return ApiResponse.ok(userService.update(user));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除用户 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(userService.delete(oid));
    }

    /** 查询用户详情 */
    @GetMapping("/{oid}")
    public ApiResponse<User> getByOid(@PathVariable String oid) {
        User user = userService.findByOid(oid);
        if (user == null) {
            return ApiResponse.fail(404, "用户不存在");
        }
        return ApiResponse.ok(user);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserController.class);

    /** 用户列表 / 搜索（拦截器自动注入 tenant_oid 过滤） */
    @GetMapping
    public ApiResponse<List<User>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String orgOid) {
        log.info("GET /api/users: keyword={}, orgOid={}, tenantOid={}",
                keyword, orgOid, cn.ck.plm.base.util.TenantContext.get());
        if (orgOid != null && !orgOid.isEmpty()) {
            List<User> result = userService.findByOrg(orgOid);
            log.info("GET /api/users findByOrg result: {} users", result != null ? result.size() : 0);
            return ApiResponse.ok(result);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            List<User> result = userService.search(keyword);
            log.info("GET /api/users search result: {} users", result != null ? result.size() : 0);
            return ApiResponse.ok(result);
        }
        List<User> result = userService.findAll();
        log.info("GET /api/users findAll result: {} users", result != null ? result.size() : 0);
        return ApiResponse.ok(result);
    }

    /** 修改密码 */
    @PutMapping("/{oid}/password")
    public ApiResponse<Void> changePassword(@PathVariable String oid,
                                            @RequestBody Map<String, String> body) {
        try {
            userService.changePassword(oid, body.get("oldPassword"), body.get("newPassword"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 重置密码（管理端） */
    @PutMapping("/{oid}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable String oid,
                                           @RequestBody Map<String, String> body) {
        try {
            userService.resetPassword(oid, body.get("newPassword"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 查询用户角色 */
    @GetMapping("/{oid}/roles")
    public ApiResponse<List<Role>> getUserRoles(@PathVariable String oid) {
        return ApiResponse.ok(userService.getUserRoles(oid));
    }

    /** 分配角色 */
    @PostMapping("/{oid}/roles")
    public ApiResponse<Void> assignRole(@PathVariable String oid,
                                        @RequestBody Map<String, String> body) {
        userService.assignRole(oid, body.get("roleOid"));
        return ApiResponse.ok();
    }

    /** 移除角色 */
    @DeleteMapping("/{oid}/roles/{roleOid}")
    public ApiResponse<Void> revokeRole(@PathVariable String oid,
                                        @PathVariable String roleOid) {
        userService.revokeRole(oid, roleOid);
        return ApiResponse.ok();
    }

    private User buildUser(Map<String, Object> body) {
        User user = new User();
        user.setUsername((String) body.get("username"));
        user.setDisplayName((String) body.get("displayName"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        user.setOrgOid((String) body.get("orgOid"));
        if (body.containsKey("enabled")) {
            user.setEnabled((Boolean) body.get("enabled"));
        }
        return user;
    }

    /**
     * 构建更新用的 User 对象，只设置前端传入的字段。
     * 未传入的字段保持 null，由 Service 层用现有值填充。
     */
    private User buildUpdateUser(String oid, Map<String, Object> body) {
        User user = new User();
        user.setOid(oid);
        if (body.containsKey("displayName")) user.setDisplayName((String) body.get("displayName"));
        if (body.containsKey("email")) user.setEmail((String) body.get("email"));
        if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
        if (body.containsKey("orgOid")) user.setOrgOid((String) body.get("orgOid"));
        if (body.containsKey("enabled")) user.setEnabled((Boolean) body.get("enabled"));
        if (body.containsKey("locked")) user.setLocked((Boolean) body.get("locked"));
        return user;
    }
}
