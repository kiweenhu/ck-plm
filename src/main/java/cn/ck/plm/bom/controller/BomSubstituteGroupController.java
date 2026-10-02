/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.dto.BomSubstituteGroupRequest;
import cn.ck.plm.bom.dto.BomSubstituteGroupVO;
import cn.ck.plm.bom.service.api.BomSubstituteGroupService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 成组替代 REST 控制器 —— BOM 行工具栏「成组替代」对应的后端。
 *
 * <p>与 {@code /api/bom-substitutes}（局部替代：一颗料换一颗料）区分开：这里换的是"一组行"，
 * 组挂在父件迭代上，带"必须整组替换"的约束。
 */
@RestController
@RequestMapping("/api/bom-substitute-groups")
public class BomSubstituteGroupController {

    @Autowired
    private BomSubstituteGroupService bomSubstituteGroupService;

    /** 某父件迭代下的全部成组替代组（含两侧成员） */
    @GetMapping("/by-parent-iteration/{parentIterationOid}")
    public ApiResponse<List<BomSubstituteGroupVO>> listByParentIteration(@PathVariable String parentIterationOid) {
        try {
            return ApiResponse.ok(bomSubstituteGroupService.listByParentIteration(parentIterationOid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询成组替代失败: " + e.getMessage());
        }
    }

    /** 新建成组替代组（状态从 DRAFT 开始） */
    @PostMapping
    public ApiResponse<BomSubstituteGroupVO> create(@RequestBody BomSubstituteGroupRequest request) {
        try {
            return ApiResponse.ok(bomSubstituteGroupService.create(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新成组替代组（两侧成员整体替换；已批准的组被改动会退回 DRAFT） */
    @PutMapping("/{oid}")
    public ApiResponse<BomSubstituteGroupVO> update(@PathVariable String oid,
                                                    @RequestBody BomSubstituteGroupRequest request) {
        try {
            return ApiResponse.ok(bomSubstituteGroupService.update(oid, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除成组替代组（成员随组级联删除） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            bomSubstituteGroupService.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 组状态流转（DRAFT / APPROVED / OBSOLETE），请求体：{ "status": "APPROVED" } */
    @PutMapping("/{oid}/status")
    public ApiResponse<BomSubstituteGroupVO> changeStatus(@PathVariable String oid,
                                                          @RequestBody Map<String, String> body) {
        try {
            String status = body == null ? null : body.get("status");
            return ApiResponse.ok(bomSubstituteGroupService.changeStatus(oid, status));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
