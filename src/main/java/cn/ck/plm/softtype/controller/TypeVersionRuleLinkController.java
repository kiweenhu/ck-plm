/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.TypeVersionRuleLink;
import cn.ck.plm.softtype.service.api.TypeVersionRuleLinkService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 类型-版本规则关联 REST 控制器。
 *
 * <p>提供类型与版本规则的绑定/解绑/查询接口。
 */
@RestController
@RequestMapping("/api/type-version-rule-links")
public class TypeVersionRuleLinkController {

    private final TypeVersionRuleLinkService service;

    public TypeVersionRuleLinkController(TypeVersionRuleLinkService service) {
        this.service = service;
    }

    /**
     * 查询某类型绑定的版本规则
     */
    @GetMapping("/type/{typeOid}")
    public ApiResponse<TypeVersionRuleLink> getByTypeOid(@PathVariable String typeOid) {
        TypeVersionRuleLink link = service.getByTypeOid(typeOid);
        return ApiResponse.ok(link);
    }

    /**
     * 为类型绑定版本规则
     */
    @PostMapping
    public ApiResponse<TypeVersionRuleLink> bind(@RequestBody Map<String, String> body) {
        try {
            String typeOid = body.get("typeOid");
            String versionRuleCode = body.get("versionRuleCode");
            if (typeOid == null || versionRuleCode == null) {
                return ApiResponse.fail(400, "typeOid 和 versionRuleCode 不能为空");
            }
            return ApiResponse.ok(service.bindRule(typeOid, versionRuleCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 解除类型的版本规则绑定
     */
    @DeleteMapping("/type/{typeOid}")
    public ApiResponse<Boolean> unbind(@PathVariable String typeOid) {
        service.unbindRule(typeOid);
        return ApiResponse.ok(true);
    }

    /**
     * 根据版本规则编码查询所有绑定了该规则的类型
     */
    @GetMapping("/rule/{versionRuleCode}")
    public ApiResponse<java.util.List<TypeVersionRuleLink>> listByRuleCode(@PathVariable String versionRuleCode) {
        return ApiResponse.ok(service.listByVersionRuleCode(versionRuleCode));
    }

    /**
     * 查询所有关联
     */
    @GetMapping
    public ApiResponse<java.util.List<TypeVersionRuleLink>> listAll() {
        return ApiResponse.ok(service.listAll());
    }
}
