/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.search;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.search.dto.SearchResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 全局搜索 REST 控制器。
 *
 * <p>供前端顶栏搜索框调用，按名称/编码模糊搜索产品系列、产品型号、零组件、文档四类核心业务对象。
 */
@RestController
@RequestMapping("/api/global-search")
public class GlobalSearchController {

    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    /**
     * @param q     搜索关键词（必填，至少 1 个非空字符）
     * @param limit 最大返回条数，默认 20，最大 100
     */
    @GetMapping
    public ApiResponse<List<SearchResultVO>> search(@RequestParam String q,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.search(q, limit));
    }
}