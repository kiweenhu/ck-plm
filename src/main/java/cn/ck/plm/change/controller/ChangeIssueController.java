/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.controller;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.mapper.ChangeIssueLookupMapper;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 变更单（ECR / ECN）挑问题时的候选接口。
 *
 * <pre>
 * GET /api/plm/change-issues/options?keyword=&limit=20
 * </pre>
 *
 * <h3>为什么不复用 /api/issues</h3>
 * <p>那个接口是「问题台账」页的列表：按状态、严重度、责任人分页，<b>没有</b>"排除已关闭"的口径。
 * 为了两个下拉去改它的签名，影响面比这里单独开一个只读接口大。这个接口只回答一件事：
 * <b>哪些问题还能被关联</b>（OPEN / IN_PROGRESS / VERIFYING）。
 *
 * <p>服务端仍会再校验一次（防绕过）—— 下拉只是减少误选，不是信任边界。
 */
@RestController
@RequestMapping("/api/plm/change-issues")
public class ChangeIssueController {

    /** 下拉最多给多少条：再多也没人翻，反而拖慢弹窗 */
    private static final int MAX_LIMIT = 100;

    private final ChangeIssueLookupMapper lookupMapper;

    public ChangeIssueController(ChangeIssueLookupMapper lookupMapper) {
        this.lookupMapper = lookupMapper;
    }

    @GetMapping("/options")
    public ApiResponse<List<ChangeIssueRef>> options(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "20") int limit) {
        String kw = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return ApiResponse.ok(lookupMapper.selectOpenIssues(kw, TenantContext.get(), size));
    }
}
