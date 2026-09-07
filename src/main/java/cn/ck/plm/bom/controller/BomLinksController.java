/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

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

    @PutMapping("/{oid}")
    public ApiResponse<BomLinks> update(@PathVariable String oid, @RequestBody BomLinks bomLinks) {
        bomLinks.setOid(oid);
        return ApiResponse.ok(bomLinksService.update(bomLinks));
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
