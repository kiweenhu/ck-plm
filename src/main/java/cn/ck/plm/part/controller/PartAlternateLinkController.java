/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.entity.PartAlternateLink;
import cn.ck.plm.part.service.api.PartAlternateLinkService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PartAlternateLink（部件双向替代关系）REST 控制器。
 */
@RestController
@RequestMapping("/api/part-alternate-links")
public class PartAlternateLinkController {

    private final PartAlternateLinkService alternateLinkService;

    public PartAlternateLinkController(PartAlternateLinkService alternateLinkService) {
        this.alternateLinkService = alternateLinkService;
    }

    @PostMapping
    public ApiResponse<PartAlternateLink> create(@RequestBody PartAlternateLink link) {
        return ApiResponse.ok(alternateLinkService.create(link));
    }

    @PutMapping("/{oid}")
    public ApiResponse<PartAlternateLink> update(@PathVariable String oid, @RequestBody PartAlternateLink link) {
        link.setOid(oid);
        return ApiResponse.ok(alternateLinkService.update(link));
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        alternateLinkService.delete(oid);
        return ApiResponse.ok();
    }

    @GetMapping("/{oid}")
    public ApiResponse<PartAlternateLink> getByOid(@PathVariable String oid) {
        return ApiResponse.ok(alternateLinkService.findByOid(oid));
    }

    /** 查询某部件作为 roleA（被替代方，roleB 为其替代件）的替代关系 */
    @GetMapping("/by-role-a/{partOid}")
    public ApiResponse<List<PartAlternateLink>> listByRoleA(@PathVariable String partOid) {
        return ApiResponse.ok(alternateLinkService.findByRoleAPart(partOid));
    }

    /** 查询某部件作为 roleB（替代方，roleA 为被其替代的部件）的替代关系 */
    @GetMapping("/by-role-b/{partOid}")
    public ApiResponse<List<PartAlternateLink>> listByRoleB(@PathVariable String partOid) {
        return ApiResponse.ok(alternateLinkService.findByRoleBPart(partOid));
    }

    /** 查询与某部件相关的全部替代关系（无论角色端） */
    @GetMapping("/by-part/{partOid}")
    public ApiResponse<List<PartAlternateLink>> listByPart(@PathVariable String partOid) {
        return ApiResponse.ok(alternateLinkService.findByPart(partOid));
    }

    @GetMapping
    public ApiResponse<List<PartAlternateLink>> listAll() {
        return ApiResponse.ok(alternateLinkService.listAll());
    }
}
