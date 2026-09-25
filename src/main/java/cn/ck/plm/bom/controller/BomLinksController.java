/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BomLinks REST 控制器。
 */
@RestController
@RequestMapping("/api/bom-links")
public class BomLinksController {

    @Autowired
    private BomLinksService bomLinksService;

    @PostMapping
    public ApiResponse<BomLinks> create(@RequestBody BomLinks bomLinks) {
        return ApiResponse.ok(bomLinksService.create(bomLinks));
    }

    /**
     * 更新 BOM 行（行号 / 数量 / 单位 / 单位成本）。
     *
     * <p>失败折成 {@code code=400 + 可读原因}：项目里没有全局异常处理器，
     * 抛出去前端只能看到「服务器错误 (500)」——而这里最需要说清的恰恰是
     * "为什么没生效"（行已被检出的工作副本取代 / 已被删除）。
     */
    @PutMapping("/{oid}")
    public ApiResponse<BomLinks> update(@PathVariable String oid, @RequestBody BomLinks bomLinks) {
        bomLinks.setOid(oid);
        try {
            return ApiResponse.ok(bomLinksService.update(bomLinks));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        bomLinksService.deleteByOid(oid);
        return ApiResponse.ok();
    }

    @GetMapping("/{oid}")
    public BomLinks getByOid(@PathVariable String oid) {
        return bomLinksService.getByOid(oid);
    }

    @GetMapping("/by-parent-iteration/{parentIterationOid}")
    public ApiResponse<List<BomLinks>> listByParentIteration(@PathVariable String parentIterationOid) {
        return ApiResponse.ok(bomLinksService.listByParentIterationOid(parentIterationOid));
    }

    /** 递归构建某父迭代下的完整多层 BOM 树（含子件展示信息） */
    @GetMapping("/tree/{parentIterationOid}")
    public ApiResponse<List<BomTreeNode>> getTree(@PathVariable String parentIterationOid) {
        return ApiResponse.ok(bomLinksService.buildTree(parentIterationOid));
    }

    /**
     * BOM 成本报告（卷积）：每一层「数量 × 单位成本」递归累加，含总成本与逐层明细。
     *
     * <p>入参用<b>父件迭代 oid</b>而不是零件主对象 oid：成本是有版本的口径，
     * A.4 与 B.1 的结构和成本都可能不同（BOM 行就挂在迭代上）。
     */
    @GetMapping("/cost-report/{parentIterationOid}")
    public ApiResponse<BomCostReportVO> getCostReport(@PathVariable String parentIterationOid) {
        return ApiResponse.ok(bomLinksService.costReport(parentIterationOid));
    }

    @GetMapping("/by-child-part/{childPartOid}")
    public ApiResponse<List<BomLinks>> listByChildPart(@PathVariable String childPartOid) {
        return ApiResponse.ok(bomLinksService.listByChildPartOid(childPartOid));
    }

    @GetMapping("/by-child-iteration/{childIterationOid}")
    public List<BomLinks> listByChildIteration(@PathVariable String childIterationOid) {
        return bomLinksService.listByChildIterationOid(childIterationOid);
    }

    @PutMapping("/{oid}/resolve-iteration")
    public void refreshResolvedIteration(@PathVariable String oid, @RequestParam String resolvedIterationOid) {
        bomLinksService.refreshResolvedIteration(oid, resolvedIterationOid);
    }

    @GetMapping
    public List<BomLinks> listAll() {
        return bomLinksService.listAll();
    }
}
