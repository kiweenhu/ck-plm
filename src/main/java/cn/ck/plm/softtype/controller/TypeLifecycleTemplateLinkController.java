/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.service.api.TypeLifecycleTemplateLinkService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 类型-生命周期模板关联 REST 控制器。
 *
 * <p>提供类型与生命周期模板的绑定/解绑/查询接口。
 */
@RestController
@RequestMapping("/api/type-lifecycle-template-links")
public class TypeLifecycleTemplateLinkController {

    private final TypeLifecycleTemplateLinkService service;

    public TypeLifecycleTemplateLinkController(TypeLifecycleTemplateLinkService service) {
        this.service = service;
    }

    /**
     * 查询某类型绑定的生命周期模板
     */
    @GetMapping("/type/{typeOid}")
    public ApiResponse<TypeLifecycleTemplateLink> getByTypeOid(@PathVariable String typeOid) {
        TypeLifecycleTemplateLink link = service.getByTypeOid(typeOid);
        return ApiResponse.ok(link);
    }

    /**
     * 为类型绑定生命周期模板
     */
    @PostMapping
    public ApiResponse<TypeLifecycleTemplateLink> bind(@RequestBody Map<String, String> body) {
        try {
            String typeOid = body.get("typeOid");
            String lifecycleTemplateCode = body.get("lifecycleTemplateCode");
            if (typeOid == null || lifecycleTemplateCode == null) {
                return ApiResponse.fail(400, "typeOid 和 lifecycleTemplateCode 不能为空");
            }
            return ApiResponse.ok(service.bindTemplate(typeOid, lifecycleTemplateCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 解除类型的生命周期模板绑定
     */
    @DeleteMapping("/type/{typeOid}")
    public ApiResponse<Boolean> unbind(@PathVariable String typeOid) {
        service.unbindTemplate(typeOid);
        return ApiResponse.ok(true);
    }

    /**
     * 根据生命周期模板编码查询所有绑定了该模板的类型
     */
    @GetMapping("/template/{lifecycleTemplateCode}")
    public ApiResponse<java.util.List<TypeLifecycleTemplateLink>> listByTemplateCode(@PathVariable String lifecycleTemplateCode) {
        return ApiResponse.ok(service.listByTemplateCode(lifecycleTemplateCode));
    }

    /**
     * 查询所有关联
     */
    @GetMapping
    public ApiResponse<java.util.List<TypeLifecycleTemplateLink>> listAll() {
        return ApiResponse.ok(service.listAll());
    }
}
