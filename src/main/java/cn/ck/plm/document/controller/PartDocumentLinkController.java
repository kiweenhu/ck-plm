/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.controller;

import cn.ck.plm.document.entity.PartDocumentLink;
import cn.ck.plm.document.service.api.PartDocumentLinkService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PartDocumentLink（部件关联文档）REST 控制器。
 *
 * <p>关联类型：REFERENCE（参考）/ DESCRIPTION（说明）。
 */
@RestController
@RequestMapping("/api/part-document-links")
public class PartDocumentLinkController {

    private final PartDocumentLinkService partDocumentLinkService;

    public PartDocumentLinkController(PartDocumentLinkService partDocumentLinkService) {
        this.partDocumentLinkService = partDocumentLinkService;
    }

    @PostMapping
    public ApiResponse<PartDocumentLink> create(@RequestBody PartDocumentLink link) {
        try {
            return ApiResponse.ok(partDocumentLinkService.create(link));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建关联失败: " + e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<PartDocumentLink> update(@PathVariable String oid, @RequestBody PartDocumentLink link) {
        try {
            link.setOid(oid);
            return ApiResponse.ok(partDocumentLinkService.update(link));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新关联失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            partDocumentLinkService.delete(oid);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除关联失败: " + e.getMessage());
        }
    }

    @GetMapping("/{oid}")
    public ApiResponse<PartDocumentLink> getByOid(@PathVariable String oid) {
        return ApiResponse.ok(partDocumentLinkService.findByOid(oid));
    }

    /** 查询某部件的关联文档（linkType 可选：REFERENCE / DESCRIPTION，不传返回全部） */
    @GetMapping("/by-part/{partOid}")
    public ApiResponse<List<PartDocumentLink>> listByPart(
            @PathVariable String partOid,
            @RequestParam(required = false) String linkType) {
        return ApiResponse.ok(partDocumentLinkService.findByPart(partOid, linkType));
    }

    @GetMapping
    public ApiResponse<List<PartDocumentLink>> listAll() {
        return ApiResponse.ok(partDocumentLinkService.listAll());
    }
}
