/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.TypeNumberRuleLink;
import cn.ck.plm.softtype.service.api.TypeNumberRuleLinkService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 类型-编码规则关联 REST 控制器。
 *
 * <p>提供类型与编码规则的绑定/解绑/查询接口。
 */
@RestController
@RequestMapping("/api/type-number-rule-links")
public class TypeNumberRuleLinkController {

    private final TypeNumberRuleLinkService service;

    public TypeNumberRuleLinkController(TypeNumberRuleLinkService service) {
        this.service = service;
    }

    /**
     * 查询某类型绑定的编码规则
     */
    @GetMapping("/type/{typeOid}")
    public ApiResponse<TypeNumberRuleLink> getByTypeOid(@PathVariable String typeOid) {
        TypeNumberRuleLink link = service.getByTypeOid(typeOid);
        return ApiResponse.ok(link);
    }

    /**
     * 为类型绑定编码规则
     */
    @PostMapping
    public ApiResponse<TypeNumberRuleLink> bind(@RequestBody Map<String, String> body) {
        try {
            String typeOid = body.get("typeOid");
            String numberRuleCode = body.get("numberRuleCode");
            if (typeOid == null || numberRuleCode == null) {
                return ApiResponse.fail(400, "typeOid 和 numberRuleCode 不能为空");
            }
            return ApiResponse.ok(service.bindRule(typeOid, numberRuleCode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 解除类型的编码规则绑定
     */
    @DeleteMapping("/type/{typeOid}")
    public ApiResponse<Boolean> unbind(@PathVariable String typeOid) {
        service.unbindRule(typeOid);
        return ApiResponse.ok(true);
    }

    /**
     * 根据编码规则编码查询所有绑定了该规则的类型
     */
    @GetMapping("/rule/{numberRuleCode}")
    public ApiResponse<java.util.List<TypeNumberRuleLink>> listByRuleCode(@PathVariable String numberRuleCode) {
        return ApiResponse.ok(service.listByNumberRuleCode(numberRuleCode));
    }

    /**
     * 查询所有关联
     */
    @GetMapping
    public ApiResponse<java.util.List<TypeNumberRuleLink>> listAll() {
        return ApiResponse.ok(service.listAll());
    }
}
