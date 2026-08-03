/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.iam.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 生命周期状态管理控制器 —— 定义业务对象可用的生命周期状态。
 *
 * <p>每个状态通过 code 唯一标识。
 * 核心状态（WORKING/APPROVING/PUBLISHED/OFFLINE/ARCHIVED）受保护不可删除。
 */
@RestController
@RequestMapping("/api/lifecycle-statuses")
public class LifecycleStatusController {

    private static final Logger log = LoggerFactory.getLogger(LifecycleStatusController.class);

    private final LifecycleStatusService lifecycleStatusService;

    public LifecycleStatusController(LifecycleStatusService lifecycleStatusService) {
        this.lifecycleStatusService = lifecycleStatusService;
    }

    // ==================== 查询 ====================

    /** 获取所有生命周期状态（按 code 升序） */
    @GetMapping
    public ApiResponse<List<LifecycleStatus>> list(@RequestParam(required = false) String keyword) {
        log.debug("查询生命周期状态列表, keyword={}", keyword);
        List<LifecycleStatus> list;
        if (keyword != null && !keyword.trim().isEmpty()) {
            list = lifecycleStatusService.search(keyword.trim());
        } else {
            list = lifecycleStatusService.findAll();
        }
        return ApiResponse.ok(list != null ? list : Collections.emptyList());
    }

    /** 获取单个生命周期状态详情 */
    @GetMapping("/{code}")
    public ApiResponse<LifecycleStatus> detail(@PathVariable String code) {
        log.debug("查询生命周期状态详情, code={}", code);
        LifecycleStatus status = lifecycleStatusService.findByCode(code);
        if (status == null) {
            return ApiResponse.fail(404, "生命周期状态 '" + code + "' 不存在");
        }
        return ApiResponse.ok(status);
    }

    // ==================== 增删改 ====================

    /** 创建生命周期状态 */
    @PostMapping
    public ApiResponse<LifecycleStatus> create(@RequestBody LifecycleStatus status) {
        log.info("创建生命周期状态: code={}, name={}", status.getCode(), status.getName());
        try {
            LifecycleStatus created = lifecycleStatusService.create(status);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建生命周期状态失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新生命周期状态（code 不可变更） */
    @PutMapping("/{code}")
    public ApiResponse<LifecycleStatus> update(@PathVariable String code, @RequestBody LifecycleStatus status) {
        log.info("更新生命周期状态: code={}", code);
        status.setCode(code);
        try {
            LifecycleStatus updated = lifecycleStatusService.update(status);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新生命周期状态失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除生命周期状态 */
    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        log.info("删除生命周期状态: code={}", code);
        try {
            boolean deleted = lifecycleStatusService.delete(code);
            if (deleted) {
                return ApiResponse.ok();
            }
            return ApiResponse.fail(404, "生命周期状态 '" + code + "' 不存在");
        } catch (IllegalStateException e) {
            log.warn("删除生命周期状态失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.warn("删除生命周期状态失败: {}", e.getMessage());
            return ApiResponse.fail(500, e.getMessage());
        }
    }
}
