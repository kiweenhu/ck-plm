/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.search;

import cn.ck.plm.search.dto.SearchResultVO;

import java.util.List;

/**
 * 全局搜索服务 —— 跨产品系列/产品型号/零组件/文档四类核心业务对象
 * 按 name / number(code) 模糊搜索。
 */
public interface GlobalSearchService {

    /**
     * @param keyword 搜索关键词（同时匹配 name 和 code/number，大小写不敏感）
     * @param limit   最大返回条数（每个子查询命中后整体截断）
     * @return 命中结果列表，按 type + code 排序
     */
    List<SearchResultVO> search(String keyword, int limit);
}