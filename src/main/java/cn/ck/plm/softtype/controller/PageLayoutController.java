/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.service.api.PageLayoutService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 页面布局控制器。
 */
@RestController
@RequestMapping("/api/page-layouts")
public class PageLayoutController {

    private final PageLayoutService service;

    public PageLayoutController(PageLayoutService service) {
        this.service = service;
    }

    /** 保存或更新布局 */
    @PostMapping
    public ApiResponse<PageLayout> save(@RequestBody PageLayout layout) {
        return ApiResponse.ok(service.saveOrUpdate(layout));
    }

    /** 查询指定实体+操作的布局 */
    @GetMapping
    public ApiResponse<PageLayout> get(@RequestParam String entityOid,
                                        @RequestParam String operationCode) {
        return ApiResponse.ok(service.findByEntityAndOperation(entityOid, operationCode));
    }

    /** 查询实体的所有操作布局 */
    @GetMapping("/all")
    public ApiResponse<List<PageLayout>> listAll(@RequestParam String entityOid,
                                                  @RequestParam String entityCode) {
        return ApiResponse.ok(service.findAllByEntity(entityOid, entityCode));
    }

    /** 删除实体+操作布局 */
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam String entityOid,
                                     @RequestParam String operationCode) {
        service.deleteByEntityAndOperation(entityOid, operationCode);
        return ApiResponse.ok();
    }

    /** 查询实体的操作摘要列表（含系统预置 + 已保存的自定义操作） */
    @GetMapping("/operations")
    public ApiResponse<List<Map<String, String>>> listOperations(@RequestParam String entityOid,
                                                                  @RequestParam String entityCode) {
        return ApiResponse.ok(service.getOperationSummary(entityOid, entityCode));
    }

    /** 按 entityCode + operationCode 查询 */
    @GetMapping("/by-code")
    public ApiResponse<PageLayout> getByCode(@RequestParam String entityCode,
                                              @RequestParam String operationCode) {
        return ApiResponse.ok(service.findByEntityCodeAndOperation(entityCode, operationCode));
    }

    /** 克隆平台布局到当前租户 */
    @PostMapping("/clone")
    public ApiResponse<PageLayout> clone(@RequestBody PageLayout layout) {
        return ApiResponse.ok(service.cloneFromPlatform(layout.getEntityOid(),
                layout.getEntityCode(), layout.getOperationCode()));
    }
}
