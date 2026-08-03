/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.TypeClassificationLink;
import cn.ck.plm.softtype.service.api.TypeClassificationLinkService;
import org.springframework.web.bind.annotation.*;

/**
 * 类型-分类关联 REST 控制器。
 */
@RestController
@RequestMapping("/api/type-definitions")
public class TypeClassificationLinkController {

    private final TypeClassificationLinkService service;

    public TypeClassificationLinkController(TypeClassificationLinkService service) {
        this.service = service;
    }

    /** 获取某类型绑定的分类 */
    @GetMapping("/{typeOid}/classification")
    public ApiResponse<TypeClassificationLink> getByTypeOid(@PathVariable String typeOid) {
        TypeClassificationLink link = service.getByTypeOid(typeOid);
        if (link == null) return ApiResponse.ok(null);
        return ApiResponse.ok(link);
    }

    /** 为类型绑定分类 */
    @PostMapping("/{typeOid}/classification")
    public ApiResponse<TypeClassificationLink> bindClassification(@PathVariable String typeOid,
                                                                   @RequestBody TypeClassificationLink body) {
        try {
            return ApiResponse.ok(service.bindClassification(typeOid, body.getClassificationOid()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 解除类型的分类绑定 */
    @DeleteMapping("/{typeOid}/classification")
    public ApiResponse<Void> unbindClassification(@PathVariable String typeOid) {
        service.unbindClassification(typeOid);
        return ApiResponse.ok();
    }
}
