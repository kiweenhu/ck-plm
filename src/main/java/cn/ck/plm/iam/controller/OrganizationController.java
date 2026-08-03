/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.Organization;
import cn.ck.plm.iam.service.api.OrganizationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织管理 REST 控制器。
 */
@RestController
@RequestMapping("/api/orgs")
public class OrganizationController {

    private final OrganizationService orgService;

    public OrganizationController(OrganizationService orgService) {
        this.orgService = orgService;
    }

    /** 创建组织 */
    @PostMapping
    public ApiResponse<Organization> create(@RequestBody Organization org) {
        try {
            return ApiResponse.ok(orgService.create(org));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新组织 */
    @PutMapping("/{oid}")
    public ApiResponse<Organization> update(@PathVariable String oid, @RequestBody Organization org) {
        try {
            org.setOid(oid);
            return ApiResponse.ok(orgService.update(org));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除组织 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        try {
            return ApiResponse.ok(orgService.delete(oid));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 查询组织详情 */
    @GetMapping("/{oid}")
    public ApiResponse<Organization> getByOid(@PathVariable String oid) {
        Organization org = orgService.findByOid(oid);
        if (org == null) {
            return ApiResponse.fail(404, "组织不存在");
        }
        return ApiResponse.ok(org);
    }

    /** 组织列表 / 搜索 / 树形 */
    @GetMapping
    public ApiResponse<List<Organization>> list(
            @RequestParam(required = false) String parentOid,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean tree) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(orgService.search(keyword));
        }
        if (tree) {
            return ApiResponse.ok(orgService.findTree());
        }
        if (parentOid != null) {
            return ApiResponse.ok(orgService.findChildren(parentOid));
        }
        return ApiResponse.ok(orgService.findAll());
    }
}
