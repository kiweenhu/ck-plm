/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.product.dto.TeamMemberVO;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.entity.Team;
import cn.ck.plm.product.service.api.ProductModelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产品型号管理 REST 控制器。
 * <p>提供产品型号的 CRUD 以及团队成员的增删查功能。
 */
@RestController
@RequestMapping("/api/product-models")
public class ProductModelController {

    private final ProductModelService productModelService;

    public ProductModelController(ProductModelService productModelService) {
        this.productModelService = productModelService;
    }

    /** 创建产品型号 */
    @PostMapping
    public ApiResponse<ProductModel> create(@RequestBody ProductModel model) {
        try {
            return ApiResponse.ok(productModelService.create(model));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新产品型号 */
    @PutMapping("/{oid}")
    public ApiResponse<ProductModel> update(@PathVariable String oid, @RequestBody ProductModel model) {
        try {
            model.setOid(oid);
            return ApiResponse.ok(productModelService.update(model));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除产品型号 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(productModelService.delete(oid));
    }

    /** 查询产品型号详情 */
    @GetMapping("/{oid}")
    public ApiResponse<ProductModel> getByOid(@PathVariable String oid) {
        ProductModel model = productModelService.findByOid(oid);
        if (model == null) {
            return ApiResponse.fail(404, "产品型号不存在");
        }
        return ApiResponse.ok(model);
    }

    /** 产品型号列表 / 搜索 */
    @GetMapping
    public ApiResponse<List<ProductModel>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productLineOid) {
        if (productLineOid != null && !productLineOid.trim().isEmpty()) {
            return ApiResponse.ok(productModelService.findByProductLineOid(productLineOid));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(productModelService.search(keyword));
        }
        return ApiResponse.ok(productModelService.findAll());
    }

    // ===== 团队管理 =====

    /** 获取产品型号关联的团队信息 */
    @GetMapping("/{oid}/team")
    public ApiResponse<Team> getTeam(@PathVariable String oid) {
        Team team = productModelService.getTeamByProductModelOid(oid);
        if (team == null) {
            return ApiResponse.fail(404, "产品型号或团队不存在");
        }
        return ApiResponse.ok(team);
    }

    /** 获取团队成员列表 */
    @GetMapping("/{oid}/team/members")
    public ApiResponse<List<TeamMemberVO>> getTeamMembers(@PathVariable String oid) {
        try {
            return ApiResponse.ok(productModelService.getTeamMembers(oid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "获取团队成员失败: " + e.getMessage());
        }
    }

    /** 添加团队成员 */
    @PostMapping("/{oid}/team/members")
    public ApiResponse<Void> addTeamMember(@PathVariable String oid,
                                           @RequestBody Map<String, String> body) {
        try {
            productModelService.addTeamMember(oid, body.get("userId"), body.get("roleName"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 移除团队成员 */
    @DeleteMapping("/{oid}/team/members/{userId}")
    public ApiResponse<Void> removeTeamMember(@PathVariable String oid,
                                               @PathVariable String userId) {
        try {
            productModelService.removeTeamMember(oid, userId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
