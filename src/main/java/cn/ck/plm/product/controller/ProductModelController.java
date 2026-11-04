/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
import cn.ck.plm.product.service.impl.ProductTeamRoleResolver;
import cn.ck.plm.product.service.impl.ProductTeamVisibility;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
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
    private final SoftTypeInstanceService softTypeInstanceService;
    private final ProductTeamVisibility productTeamVisibility;
    private final ProductTeamRoleResolver productTeamRoleResolver;

    /** 能力宿主 code（与 TypeDefinition.rootTypeCode 一致） */
    private static final String HOST = "PRODUCT_MODEL";

    public ProductModelController(ProductModelService productModelService,
                                  SoftTypeInstanceService softTypeInstanceService,
                                  ProductTeamVisibility productTeamVisibility,
                                  ProductTeamRoleResolver productTeamRoleResolver) {
        this.productModelService = productModelService;
        this.softTypeInstanceService = softTypeInstanceService;
        this.productTeamVisibility = productTeamVisibility;
        this.productTeamRoleResolver = productTeamRoleResolver;
    }

    /**
     * 创建产品型号。
     *
     * <p>创建编排已收敛到 {@code ProductModelInstanceCreator}，本方法复用同一实现。
     * 入参由 {@code ProductModel} 改为 {@code Map}：与创建策略的载荷形态一致，
     * 实体映射由策略内部统一完成，避免两处各写一遍转换。
     */
    @PostMapping
    public ApiResponse<ProductModel> create(@RequestBody Map<String, Object> body) {
        try {
            Object typeCode = body.get("typeDefinitionCode");
            SoftTypeInstanceResult result = softTypeInstanceService.createForHost(
                    HOST, typeCode != null ? typeCode.toString() : HOST, body);
            return ApiResponse.ok((ProductModel) result.getEntity());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新产品型号 —— 仅产品经理（团队角色，型号继承父系列团队）或管理员可维护（2026-10-17 用户口径） */
    @PutMapping("/{oid}")
    public ApiResponse<ProductModel> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可编辑产品型号");
        }
        try {
            // 更新编排已收敛到 ProductModelServiceImpl#updateInstance，与统一入口共用同一实现
            Object entity = softTypeInstanceService.updateForHost(HOST, oid, body);
            return ApiResponse.ok((ProductModel) entity);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        }
    }

    /** 删除产品型号（逻辑删除，进入回收站）—— 仅产品经理（团队角色）或管理员 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可删除产品型号");
        }
        return ApiResponse.ok(productModelService.delete(oid));
    }

    /** 查询回收站（逻辑删除的产品型号）—— 仅管理员可见（口径同前端：回收站只对租户管理员开放） */
    @GetMapping("/deleted")
    public ApiResponse<List<ProductModel>> listDeleted() {
        if (!productTeamVisibility.isAdmin()) {
            return ApiResponse.fail(403, "回收站仅租户管理员可见");
        }
        return ApiResponse.ok(productModelService.findDeleted());
    }

    /** 恢复产品型号（从回收站）—— 仅管理员可操作 */
    @PostMapping("/{oid}/restore")
    public ApiResponse<Boolean> restore(@PathVariable String oid) {
        if (!productTeamVisibility.isAdmin()) {
            return ApiResponse.fail(403, "回收站仅租户管理员可操作");
        }
        return ApiResponse.ok(productModelService.restore(oid));
    }

    /** 查询产品型号详情 */
    /**
     * 按 OID 查询产品型号。
     *
     * <p>读取编排已收敛到 {@code ProductModelInstanceCreator#get}，与统一入口共用同一实现。
     */
    @GetMapping("/{oid}")
    public ApiResponse<Object> getByOid(@PathVariable String oid) {
        Object entity = softTypeInstanceService.getForHost(HOST, oid, null);
        if (entity == null) {
            return ApiResponse.fail(404, "产品型号不存在");
        }
        // 读取路径返回的是摊平了 IBA 的 Map（见 ProductModelServiceImpl#withIba）；
        // 原先硬转 ProductModel，每次读详情都 ClassCastException → 500（与产品系列同一处问题）
        return ApiResponse.ok(entity);
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

    /** 添加团队成员 —— 仅产品经理（团队角色）或管理员 */
    @PostMapping("/{oid}/team/members")
    public ApiResponse<Void> addTeamMember(@PathVariable String oid,
                                           @RequestBody Map<String, String> body) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可维护团队");
        }
        try {
            productModelService.addTeamMember(oid, body.get("userId"), body.get("roleName"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 移除团队成员 —— 仅产品经理（团队角色）或管理员 */
    @DeleteMapping("/{oid}/team/members/{userId}")
    public ApiResponse<Void> removeTeamMember(@PathVariable String oid,
                                               @PathVariable String userId) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可维护团队");
        }
        try {
            productModelService.removeTeamMember(oid, userId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
