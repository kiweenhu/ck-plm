/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.StageTemplate;
import cn.ck.plm.base.service.api.StageTemplateService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 研发阶段模板 REST 控制器。
 */
@RestController
@RequestMapping("/api/stage-templates")
public class StageTemplateController {

    private final StageTemplateService service;

    public StageTemplateController(StageTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<StageTemplate>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/{code}")
    public ApiResponse<StageTemplate> getByCode(@PathVariable String code) {
        StageTemplate tmpl = service.findByCode(code);
        if (tmpl == null) return ApiResponse.fail(404, "阶段模板不存在");
        return ApiResponse.ok(tmpl);
    }

    @PostMapping
    public ApiResponse<StageTemplate> create(@RequestBody StageTemplate template) {
        try {
            return ApiResponse.ok(service.create(template));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<StageTemplate> update(@PathVariable String oid, @RequestBody StageTemplate template) {
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

    /** 租户克隆平台级模板到本租户 */
    @PostMapping("/clone-from-platform")
    public ApiResponse<Integer> cloneFromPlatform() {
        try {
            int count = service.cloneFromPlatform();
            return ApiResponse.ok(count);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }
}
