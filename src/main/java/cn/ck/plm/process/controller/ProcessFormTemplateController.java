/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.entity.ProcessFormTemplate;
import cn.ck.plm.process.service.api.ProcessFormTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程表单模板控制器（业务配置 → 流程表单）。
 *
 * <p>入参是表单模板实体本身（与 {@code PageLayoutController} 同风格），
 * 校验失败折成 {@link ApiResponse#fail(int, String)}：项目里没有全局异常处理器，
 * 直接把异常抛出去前端只能看到「服务器错误 (500)」，看不到"为什么不行"。
 */
@RestController
@RequestMapping("/api/form-templates")
public class ProcessFormTemplateController {

    private final ProcessFormTemplateService service;

    public ProcessFormTemplateController(ProcessFormTemplateService service) {
        this.service = service;
    }

    /** 全部模板（平台内置 + 本租户自定义） */
    @GetMapping
    public ApiResponse<List<ProcessFormTemplate>> list() {
        return ApiResponse.ok(service.list());
    }

    /** 某节点类型在设计期可选的模板（仅启用项；设计器下拉用） */
    @GetMapping("/for-node")
    public ApiResponse<List<ProcessFormTemplate>> listForNode(@RequestParam String nodeType) {
        return ApiResponse.ok(service.listForNodeType(nodeType));
    }

    /** 新建自定义表单模板 */
    @PostMapping
    public ApiResponse<ProcessFormTemplate> create(@RequestBody ProcessFormTemplate template) {
        try {
            return ApiResponse.ok(service.create(template));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 修改模板（内置模板只允许改名称/说明/启用/排序） */
    @PutMapping("/{oid}")
    public ApiResponse<ProcessFormTemplate> update(@PathVariable String oid,
                                                   @RequestBody ProcessFormTemplate template) {
        template.setOid(oid);
        try {
            return ApiResponse.ok(service.update(template));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除模板（内置模板不可删除） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            service.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
