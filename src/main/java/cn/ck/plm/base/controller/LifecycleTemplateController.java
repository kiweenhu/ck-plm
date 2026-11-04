/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.LifecycleTemplateDef;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.iam.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 生命周期模板管理控制器 —— 定义状态流转规则。
 *
 * <p>每个模板包含一组状态及状态间的升版（PROMOTE）和驳回（REJECT）流转规则。
 * 模板采用 Master-Iteration 复合实体模式，支持版本控制。
 */
@RestController
@RequestMapping("/api/lifecycle-templates")
public class LifecycleTemplateController {

    private static final Logger log = LoggerFactory.getLogger(LifecycleTemplateController.class);

    private final LifecycleTemplateService templateService;

    public LifecycleTemplateController(LifecycleTemplateService templateService) {
        this.templateService = templateService;
    }

    // ==================== 查询 ====================

    @GetMapping
    public ApiResponse<List<LifecycleTemplateMaster>> list(@RequestParam(required = false) String keyword) {
        log.debug("查询生命周期模板列表, keyword={}", keyword);
        List<LifecycleTemplateMaster> list;
        if (keyword != null && !keyword.trim().isEmpty()) {
            list = templateService.search(keyword.trim());
        } else {
            list = templateService.findAll();
        }
        return ApiResponse.ok(list != null ? list : Collections.emptyList());
    }

    @GetMapping("/{code}")
    public ApiResponse<LifecycleTemplateMaster> detail(@PathVariable String code) {
        log.debug("查询生命周期模板详情, code={}", code);
        LifecycleTemplateMaster template = templateService.findByCode(code);
        if (template == null) {
            return ApiResponse.fail(404, "生命周期模板 '" + code + "' 不存在");
        }
        return ApiResponse.ok(template);
    }

    @GetMapping("/{code}/domain")
    public ApiResponse<LifecycleTemplateDef> domainDef(@PathVariable String code) {
        log.debug("查询生命周期模板领域模型, code={}", code);
        LifecycleTemplateMaster template = templateService.findByCode(code);
        if (template == null) {
            return ApiResponse.fail(404, "生命周期模板 '" + code + "' 不存在");
        }
        return ApiResponse.ok(template.toDomainDef());
    }

    // ==================== 增删改 ====================

    @PostMapping
    public ApiResponse<LifecycleTemplateMaster> create(@RequestBody LifecycleTemplateMaster template) {
        log.info("创建生命周期模板: code={}, name={}", template.getCode(), template.getName());
        try {
            LifecycleTemplateMaster created = templateService.create(template);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建生命周期模板失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{code}")
    public ApiResponse<LifecycleTemplateMaster> update(@PathVariable String code, @RequestBody LifecycleTemplateMaster template) {
        log.info("更新生命周期模板: code={}", code);
        template.setCode(code);
        try {
            LifecycleTemplateMaster updated = templateService.update(template);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新生命周期模板失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 设置某状态是否允许修订 —— 由租户管理员在「业务配置 → 生命周期」按状态决定。
     *
     * <pre>
     * PUT /api/lifecycle-templates/DOC_REVIEW/states/PUBLISHED/revisable
     * { "revisable": false }
     * </pre>
     *
     * <p>这是权限开关而不是结构变更：不产生新的模板子版本，改动写到该模板全部子版本，
     * 在途对象（固化了旧子版本）也立即按新规则判定。执行侧见
     * {@code LifecycleTemplateService#ensureRevisable}。
     */
    @PutMapping("/{code}/states/{statusCode}/revisable")
    public ApiResponse<Void> setStateRevisable(@PathVariable String code,
                                               @PathVariable String statusCode,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Object value = body != null ? body.get("revisable") : null;
        if (!(value instanceof Boolean)) {
            return ApiResponse.fail(400, "revisable 必须为布尔值");
        }
        try {
            templateService.setStateRevisable(code, statusCode, (Boolean) value);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            log.warn("设置状态可修订失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("设置状态可修订被拒: {}", e.getMessage());
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            log.warn("设置状态可修订异常: template={}, status={}, error={}", code, statusCode, e.getMessage());
            return ApiResponse.fail(500, "设置状态可修订失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        log.info("删除生命周期模板: code={}", code);
        try {
            boolean deleted = templateService.delete(code);
            if (deleted) {
                return ApiResponse.ok();
            }
            return ApiResponse.fail(404, "生命周期模板 '" + code + "' 不存在");
        } catch (Exception e) {
            log.warn("删除生命周期模板失败: {}", e.getMessage());
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    // 注：「状态 → 流程模板」绑定的接口在 TypeLifecycleTemplateLinkController
    //（/api/type-lifecycle-template-links/type/{typeOid}/state-processes）——
    // 主语是【类型】而不是生命周期模板：同一个模板会被多个类型复用，各自可绑不同流程。
}
