/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.RuleTemplate;
import cn.ck.plm.base.service.api.RuleTemplateService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检查规则模板 REST 控制器 —— 与前面四类模板控制器同构
 * （含 {@code PUT /{oid}/disabled} 停用开关：企业裁剪用停用而非删除）。
 */
@RestController
@RequestMapping("/api/rule-templates")
public class RuleTemplateController {

    private final RuleTemplateService service;

    public RuleTemplateController(RuleTemplateService service) {
        this.service = service;
    }

    /** 本租户检查规则（可按行业/方法论过滤；含已停用，由界面决定显隐） */
    @GetMapping
    public ApiResponse<List<RuleTemplate>> list(
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.findAll(industry));
    }

    @GetMapping("/{code}")
    public ApiResponse<RuleTemplate> getByCode(@PathVariable String code) {
        RuleTemplate tmpl = service.findByCode(code);
        if (tmpl == null) return ApiResponse.fail(404, "检查规则不存在");
        return ApiResponse.ok(tmpl);
    }

    @PostMapping
    public ApiResponse<RuleTemplate> create(@RequestBody RuleTemplate template) {
        try {
            return ApiResponse.ok(service.create(template));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<RuleTemplate> update(@PathVariable String oid,
                                            @RequestBody RuleTemplate template) {
        try {
            template.setOid(oid);
            return ApiResponse.ok(service.update(template));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        try {
            return ApiResponse.ok(service.delete(oid));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
    @PutMapping("/{oid}/disabled")
    public ApiResponse<Boolean> setDisabled(@PathVariable String oid,
                                            @RequestParam boolean value) {
        try {
            return ApiResponse.ok(service.setDisabled(oid, value));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /** 租户克隆平台级模板到本租户（幂等：已存在的 code 跳过） */
    @PostMapping("/clone-from-platform")
    public ApiResponse<Integer> cloneFromPlatform(@RequestParam(required = false) String industry) {
        try {
            return ApiResponse.ok(service.cloneFromPlatform(industry));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /** 查询平台提供的行业/方法论列表 */
    @GetMapping("/platform-industries")
    public ApiResponse<List<String>> listPlatformIndustries() {
        return ApiResponse.ok(service.listPlatformIndustries());
    }

    /** 查询平台级模板（按 industry 过滤，空返回全部） */
    @GetMapping("/platform")
    public ApiResponse<List<RuleTemplate>> findPlatformTemplates(
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.findPlatformTemplates(industry));
    }
}
