/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.product.dto.TeamMemberVO;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.entity.Team;
import cn.ck.plm.product.service.api.ProductLineService;
import cn.ck.plm.product.service.api.ProductModelService;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产品系列管理 REST 控制器。
 * <p>提供产品线的 CRUD 以及团队成员的增删查功能。
 */
@RestController
@RequestMapping("/api/product-lines")
public class ProductLineController {

    private final ProductLineService productLineService;
    private final ProductModelService productModelService;
    private final IbaDataSupport ibaDataSupport;
    private final ObjectMapper objectMapper;
    private final SoftTypeInstanceService softTypeInstanceService;

    private static final String IBA_ENTITY_TYPE = "PRODUCT_LINE";

    public ProductLineController(ProductLineService productLineService, ProductModelService productModelService,
                                  IbaDataSupport ibaDataSupport, ObjectMapper objectMapper,
                                  SoftTypeInstanceService softTypeInstanceService) {
        this.productLineService = productLineService;
        this.productModelService = productModelService;
        this.ibaDataSupport = ibaDataSupport;
        this.objectMapper = objectMapper;
        this.softTypeInstanceService = softTypeInstanceService;
    }

    /**
     * 创建产品线。
     *
     * <p>创建编排已收敛到 {@code ProductLineInstanceCreator}，本方法复用同一实现。
     */
    @PostMapping
    public ApiResponse<ProductLine> create(@RequestBody Map<String, Object> body) {
        try {
            String typeCode = ibaDataSupport.getString(body, "typeDefinitionCode");
            SoftTypeInstanceResult result = softTypeInstanceService.createForHost(
                    IBA_ENTITY_TYPE, typeCode != null ? typeCode : IBA_ENTITY_TYPE, body);
            return ApiResponse.ok((ProductLine) result.getEntity());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新产品线 */
    @PutMapping("/{oid}")
    public ApiResponse<ProductLine> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            // 更新编排已收敛到 ProductLineServiceImpl#updateInstance，与统一入口共用同一实现
            Object entity = softTypeInstanceService.updateForHost(IBA_ENTITY_TYPE, oid, body);
            return ApiResponse.ok((ProductLine) entity);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        }
    }

    /** 删除产品线（逻辑删除，进入回收站） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(productLineService.delete(oid));
    }

    /** 查询回收站（逻辑删除的产品系列） */
    @GetMapping("/deleted")
    public ApiResponse<List<ProductLine>> listDeleted() {
        return ApiResponse.ok(productLineService.findDeleted());
    }

    /** 恢复产品系列（从回收站） */
    @PostMapping("/{oid}/restore")
    public ApiResponse<Boolean> restore(@PathVariable String oid) {
        return ApiResponse.ok(productLineService.restore(oid));
    }

    /**
     * 查询产品线详情（同时支持产品系列和产品型号）。
     *
     * <p>「系列优先、型号回退并转换形态」的读取编排已收敛到
     * {@code ProductLineInstanceCreator#get}，与统一入口共用同一实现。
     */
    @GetMapping("/{oid}")
    public ApiResponse<ProductLine> getByOid(@PathVariable String oid) {
        Object entity = softTypeInstanceService.getForHost(IBA_ENTITY_TYPE, oid, null);
        if (entity == null) {
            return ApiResponse.fail(404, "产品线或产品型号不存在");
        }
        return ApiResponse.ok((ProductLine) entity);
    }

    /** 产品线列表 / 搜索 */
    @GetMapping
    public ApiResponse<List<ProductLine>> list(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(productLineService.search(keyword));
        }
        return ApiResponse.ok(productLineService.findAll());
    }

    /** 产品线树（嵌套 children 结构，含子系列 + 产品型号） */
    @GetMapping("/tree")
    public ApiResponse<List<ProductLine>> tree() {
        return ApiResponse.ok(productLineService.findTree());
    }

    /** 纯产品系列树（嵌套 children 结构，仅子系列，不含产品型号，用于 product-line-select 控件） */
    @GetMapping("/tree-lines-only")
    public ApiResponse<List<ProductLine>> treeLinesOnly() {
        return ApiResponse.ok(productLineService.findLinesOnlyTree());
    }

    /** 产品线根节点列表 */
    @GetMapping("/roots")
    public ApiResponse<List<ProductLine>> roots() {
        return ApiResponse.ok(productLineService.findRoots());
    }

    /** 查询指定父节点的子节点 */
    @GetMapping("/children/{parentOid}")
    public ApiResponse<List<ProductLine>> children(@PathVariable String parentOid) {
        return ApiResponse.ok(productLineService.findChildren(parentOid));
    }

    /** 批量获取产品线统计（子系列数量 + 产品型号数量） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Map<String, Integer>>> stats() {
        return ApiResponse.ok(productLineService.getStats());
    }


    // ===== 团队管理 =====

    /** 获取产品线关联的团队信息 */
    @GetMapping("/{oid}/team")
    public ApiResponse<Team> getTeam(@PathVariable String oid) {
        Team team = productLineService.getTeamByProductLineOid(oid);
        if (team == null) {
            return ApiResponse.fail(404, "产品线或团队不存在");
        }
        return ApiResponse.ok(team);
    }

    /** 获取团队成员列表 */
    @GetMapping("/{oid}/team/members")
    public ApiResponse<List<TeamMemberVO>> getTeamMembers(@PathVariable String oid) {
        try {
            return ApiResponse.ok(productLineService.getTeamMembers(oid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "获取团队成员失败: " + e.getMessage());
        }
    }

    /** 添加团队成员 */
    @PostMapping("/{oid}/team/members")
    public ApiResponse<Void> addTeamMember(@PathVariable String oid,
                                           @RequestBody Map<String, String> body) {
        try {
            productLineService.addTeamMember(oid, body.get("userId"), body.get("roleName"));
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
            productLineService.removeTeamMember(oid, userId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
