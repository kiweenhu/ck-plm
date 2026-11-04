/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.ReviewTemplate;
import cn.ck.plm.base.service.api.ReviewTemplateService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评审模板 REST 控制器 —— 与阶段 / 交付件 / 门模板控制器同构
 * （含 {@code PUT /{oid}/disabled} 停用开关：企业裁剪用停用而非删除）。
 */
@RestController
@RequestMapping("/api/review-templates")
public class ReviewTemplateController {

    private final ReviewTemplateService service;

    public ReviewTemplateController(ReviewTemplateService service) {
        this.service = service;
    }

    /** 本租户评审模板（可按行业/方法论过滤；含已停用，由界面决定显隐） */
    @GetMapping
    public ApiResponse<List<ReviewTemplate>> list(
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.findAll(industry));
    }

    @GetMapping("/{code}")
    public ApiResponse<ReviewTemplate> getByCode(@PathVariable String code) {
        ReviewTemplate tmpl = service.findByCode(code);
        if (tmpl == null) return ApiResponse.fail(404, "评审模板不存在");
        return ApiResponse.ok(tmpl);
    }

    @PostMapping
    public ApiResponse<ReviewTemplate> create(@RequestBody ReviewTemplate template) {
        try {
            return ApiResponse.ok(service.create(template));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<ReviewTemplate> update(@PathVariable String oid,
                                              @RequestBody ReviewTemplate template) {
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
    public ApiResponse<List<ReviewTemplate>> findPlatformTemplates(
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.findPlatformTemplates(industry));
    }
}
