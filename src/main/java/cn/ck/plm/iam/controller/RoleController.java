/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.service.api.RoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理 REST 控制器。
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
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
