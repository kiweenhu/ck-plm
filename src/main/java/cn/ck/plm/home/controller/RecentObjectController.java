/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.home.controller;

import cn.ck.plm.home.dto.RecentObjectVO;
import cn.ck.plm.home.service.api.RecentObjectService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 个人中心（工作台）REST API。
 *
 * <pre>
 * GET /api/home/recent-objects?days=5&amp;limit=20   我最近创建/修改过的对象
 * </pre>
 *
 * <p>当前用户与租户取自请求上下文（{@code UserContext} / {@code TenantContext}），
 * 不接受前端传入用户 —— 这类"我的"接口一旦能被指定用户，就是越权读取的入口。
 */
@RestController
@RequestMapping("/api/home")
public class RecentObjectController {

    private static final Logger log = LoggerFactory.getLogger(RecentObjectController.class);

    private final RecentObjectService recentObjectService;

    public RecentObjectController(RecentObjectService recentObjectService) {
        this.recentObjectService = recentObjectService;
    }

    /**
     * 我最近创建/修改过的业务对象。
     *
     * <p>数据源是对象表自身的创建/修改人 + 时间戳（最后状态口径），
     * <b>不是</b>操作流水 {@code ck_user_activity} —— 后者只记页面访问与检出类操作，
     * 既不记"创建/修改对象"，也不带对象 oid，回答不了这个问题。
     *
     * @param days  时间窗口（天，默认 5，上限 90）
     * @param limit 条数上限（默认 20，上限 50）
     */
    @GetMapping("/recent-objects")
    public ApiResponse<List<RecentObjectVO>> recentObjects(@RequestParam(defaultValue = "5") int days,
                                                           @RequestParam(defaultValue = "20") int limit) {
        try {
            return ApiResponse.ok(recentObjectService.findMyRecentObjects(days, limit));
        } catch (Exception e) {
            log.error("查询我最近创建/修改的对象失败: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "查询我最近创建/修改的对象失败: " + e.getMessage());
        }
    }
}
