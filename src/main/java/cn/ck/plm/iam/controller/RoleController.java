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
import cn.ck.plm.iam.service.api.RoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理 REST 控制器：角色 CRUD + 平台角色 + 角色成员管理。
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** 查询全部平台级角色（精确路径，优先于 /{oid} 匹配） */
    @GetMapping("/platform")
    public ApiResponse<List<Role>> platformRoles() {
        return ApiResponse.ok(roleService.findPlatformRoles());
    }

    /** 查询当前租户的管理员角色（TENANT_ADMIN）及其成员 */
    @GetMapping("/admin-members")
    public ApiResponse<java.util.Map<String, Object>> adminMembers() {
        return ApiResponse.ok(roleService.findAdminMembers());
    }

    /** 查询某角色的成员用户列表 */
    @GetMapping("/{oid}/members")
    public ApiResponse<List<User>> roleMembers(@PathVariable String oid) {
        return ApiResponse.ok(roleService.findRoleMembers(oid));
    }

    /** 添加角色成员（body: { userOid }） */
    @PostMapping("/{oid}/members")
    public ApiResponse<Void> addRoleMember(@PathVariable String oid, @RequestBody Map<String, String> body) {
        try {
            roleService.addRoleMember(oid, body.get("userOid"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "添加成员失败: " + e.getMessage());
        }
    }

    /** 移除角色成员 */
    @DeleteMapping("/{oid}/members/{userOid}")
    public ApiResponse<Void> removeRoleMember(@PathVariable String oid, @PathVariable String userOid) {
        try {
            roleService.removeRoleMember(oid, userOid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "移除成员失败: " + e.getMessage());
        }
    }

    /** 创建角色 */
    @PostMapping
    public ApiResponse<Role> create(@RequestBody Role role) {
        try {
            return ApiResponse.ok(roleService.create(role));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新角色 */
    @PutMapping("/{oid}")
    public ApiResponse<Role> update(@PathVariable String oid, @RequestBody Role role) {
        try {
            role.setOid(oid);
            return ApiResponse.ok(roleService.update(role));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除角色 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(roleService.delete(oid));
    }

    /** 查询角色详情 */
    @GetMapping("/{oid}")
    public ApiResponse<Role> getByOid(@PathVariable String oid) {
        Role role = roleService.findByOid(oid);
        if (role == null) {
            return ApiResponse.fail(404, "角色不存在");
        }
        return ApiResponse.ok(role);
    }

    /** 角色列表 / 搜索 */
    @GetMapping
    public ApiResponse<List<Role>> list(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(roleService.search(keyword));
        }
        return ApiResponse.ok(roleService.findAll());
    }
}
