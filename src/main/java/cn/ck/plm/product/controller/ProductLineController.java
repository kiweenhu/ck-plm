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
import cn.ck.plm.softtype.service.api.IBADataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
    private final IBADataService ibaDataService;
    private final ObjectMapper objectMapper;

    private static final String IBA_ENTITY_TYPE = "PRODUCT_LINE";

    public ProductLineController(ProductLineService productLineService, ProductModelService productModelService,
                                  IBADataService ibaDataService, ObjectMapper objectMapper) {
        this.productLineService = productLineService;
        this.productModelService = productModelService;
        this.ibaDataService = ibaDataService;
        this.objectMapper = objectMapper;
    }

    /** 创建产品线 */
    @PostMapping
    public ApiResponse<ProductLine> create(@RequestBody Map<String, Object> body) {
        try {
            ProductLine productLine = objectMapper.convertValue(body, ProductLine.class);
            ProductLine created = productLineService.create(productLine);
            // 新建：全量保存 IBA（无旧数据，delete-all 无副作用）
            saveIbaValues(IBA_ENTITY_TYPE, created.getOid(), body);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新产品线 */
    @PutMapping("/{oid}")
    public ApiResponse<ProductLine> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            ProductLine productLine = objectMapper.convertValue(body, ProductLine.class);
            productLine.setOid(oid);
            ProductLine updated = productLineService.update(productLine);
            // 更新：合并保存 IBA（保留已持久化但本次未提交的字段）
            mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 提取请求体中的 IBA 值 */
    private Map<String, Object> extractIbaFromBody(Map<String, Object> body) {
        Map<String, Object> ibaValues = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null || "".equals(val)) continue;
            if (isEntityField(key)) continue;
            ibaValues.put(key, val);
        }
        return ibaValues;
    }

    /** 新建时全量保存 IBA 属性（delete-all + insert-all） */
    private void saveIbaValues(String entityType, String entityOid, Map<String, Object> body) {
        Map<String, Object> ibaValues = extractIbaFromBody(body);
        if (!ibaValues.isEmpty()) {
            ibaDataService.saveValues(entityType, entityOid, ibaValues);
        }
    }

    /** 更新时合并保存 IBA 属性（与已有数据合并，避免误删未提交字段） */
    private void mergeIbaValues(String entityType, String entityOid, Map<String, Object> body) {
        Map<String, Object> ibaValues = extractIbaFromBody(body);
        if (!ibaValues.isEmpty()) {
            ibaDataService.mergeValues(entityType, entityOid, ibaValues);
        }
    }

    /** 判断字段名是否为 ProductLine 实体的已知属性（含 Jackson 可能序列化的布尔 getter） */
    private boolean isEntityField(String key) {
        return "oid".equals(key) || "code".equals(key) || "name".equals(key)
                || "description".equals(key) || "thumbnail".equals(key)
                || "teamOid".equals(key) || "parentOid".equals(key)
                || "creator".equals(key) || "createdAt".equals(key)
                || "updater".equals(key) || "updatedAt".equals(key)
                || "children".equals(key) || "nodeType".equals(key)
                || "icon".equals(key)
                || "new".equals(key) || "persisted".equals(key); // BaseEntity.isNew()/isPersisted() 的 Jackson 序列化产物
    }

    /** 删除产品线 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(productLineService.delete(oid));
    }

    /** 查询产品线详情（同时支持产品系列和产品型号） */
    @GetMapping("/{oid}")
    public ApiResponse<ProductLine> getByOid(@PathVariable String oid) {
        // 先查产品系列
        ProductLine pl = productLineService.findByOid(oid);
        if (pl != null) {
            pl.setNodeType("PRODUCT_LINE");
            return ApiResponse.ok(pl);
        }
        // 再查产品型号
        ProductModel model = productModelService.findByOid(oid);
        if (model != null) {
            // 将 ProductModel 转换为 ProductLine 格式返回
            ProductLine result = new ProductLine();
            result.setOid(model.getOid());
            result.setCode(model.getCode());
            result.setName(model.getName());
            result.setDescription(model.getDescription());
            result.setThumbnail(model.getThumbnail());
            result.setTeamOid(model.getTeamOid());
            result.setParentOid(model.getParentOid()); // 产品型号的父级是产品系列
            result.setNodeType("PRODUCT_MODEL"); // 标记为产品型号
            return ApiResponse.ok(result);
        }
        return ApiResponse.fail(404, "产品线或产品型号不存在");
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
