/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.entity.ViewTransition;
import cn.ck.plm.base.service.api.ViewService;
import cn.ck.plm.base.service.api.ViewTransitionService;
import cn.ck.plm.iam.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 视图定义管理控制器 —— 定义业务对象可用的视图及视图间的切换规则。
 *
 * <p>View 定义业务视角（Design / Manufacturing / Service），
 * ViewTransition 定义视图之间的切换规则（含生命周期状态前置条件和最新小版本要求）。
 */
@RestController
@RequestMapping("/api/views")
public class ViewController {

    private static final Logger log = LoggerFactory.getLogger(ViewController.class);

    private final ViewService viewService;
    private final ViewTransitionService transitionService;

    public ViewController(ViewService viewService, ViewTransitionService transitionService) {
        this.viewService = viewService;
        this.transitionService = transitionService;
    }

    // ==================== 视图 CRUD ====================

    /** 获取所有视图（支持 keyword 模糊搜索） */
    @GetMapping
    public ApiResponse<List<View>> list(@RequestParam(required = false) String keyword) {
        log.debug("查询视图列表, keyword={}", keyword);
        List<View> list;
        if (keyword != null && !keyword.trim().isEmpty()) {
            list = viewService.search(keyword.trim());
        } else {
            list = viewService.findAll();
        }
        return ApiResponse.ok(list != null ? list : Collections.emptyList());
    }

    /** 获取所有已启用的视图 */
    @GetMapping("/enabled")
    public ApiResponse<List<View>> listEnabled() {
        log.debug("查询已启用视图列表");
        List<View> list = viewService.findAllEnabled();
        return ApiResponse.ok(list != null ? list : Collections.emptyList());
    }

    /** 获取单个视图详情 */
    @GetMapping("/{code}")
    public ApiResponse<View> detail(@PathVariable String code) {
        log.debug("查询视图详情, code={}", code);
        View view = viewService.findByCode(code);
        if (view == null) {
            return ApiResponse.fail(404, "视图 '" + code + "' 不存在");
        }
        return ApiResponse.ok(view);
    }

    /** 创建视图 */
    @PostMapping
    public ApiResponse<View> create(@RequestBody View view) {
        log.info("创建视图: code={}, name={}", view.getCode(), view.getName());
        try {
            View created = viewService.create(view);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建视图失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新视图（code 不可变更） */
    @PutMapping("/{code}")
    public ApiResponse<View> update(@PathVariable String code, @RequestBody View view) {
        log.info("更新视图: code={}", code);
        view.setCode(code);
        try {
            View updated = viewService.update(view);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新视图失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除视图（级联删除关联切换规则） */
    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        log.info("删除视图: code={}", code);
        boolean deleted = viewService.delete(code);
        if (deleted) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(404, "视图 '" + code + "' 不存在");
    }

    // ==================== 视图切换规则 CRUD ====================

    /** 获取指定视图的切换规则列表 */
    @GetMapping("/{viewCode}/transitions")
    public ApiResponse<List<ViewTransition>> listTransitions(@PathVariable String viewCode) {
        log.debug("查询视图切换规则, fromViewCode={}", viewCode);
        List<ViewTransition> list = transitionService.findByFromViewCode(viewCode);
        return ApiResponse.ok(list != null ? list : Collections.emptyList());
    }

    /** 创建视图切换规则 */
    @PostMapping("/transitions")
    public ApiResponse<ViewTransition> createTransition(@RequestBody ViewTransition transition) {
        log.info("创建视图切换规则: {} → {}", transition.getFromViewCode(), transition.getToViewCode());
        try {
            ViewTransition created = transitionService.create(transition);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建视图切换规则失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新视图切换规则 */
    @PutMapping("/transitions/{oid}")
    public ApiResponse<ViewTransition> updateTransition(@PathVariable String oid, @RequestBody ViewTransition transition) {
        log.info("更新视图切换规则: oid={}", oid);
        transition.setOid(oid);
        try {
            ViewTransition updated = transitionService.update(transition);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新视图切换规则失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除视图切换规则 */
    @DeleteMapping("/transitions/{oid}")
    public ApiResponse<Void> deleteTransition(@PathVariable String oid) {
        log.info("删除视图切换规则: oid={}", oid);
        boolean deleted = transitionService.delete(oid);
        if (deleted) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(404, "视图切换规则不存在");
    }
}
