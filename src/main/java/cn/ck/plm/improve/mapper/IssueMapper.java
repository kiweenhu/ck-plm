/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.mapper;

import cn.ck.plm.improve.entity.Issue;

import java.util.List;
import java.util.Map;

/**
 * 问题 Mapper。
 *
 * <p>本表不在 {@code TenantStatementInterceptor} 的共享名单里 → 每条 SQL 都<b>显式</b>
 * 书写 {@code tenant_oid}（语义不依赖拦截器注入，固定为"只本租户"）。
 */
public interface IssueMapper {

    int insert(Issue issue);

    /** 全字段更新（patch 语义由服务层负责合并后传入） */
    int update(Issue issue);

    int deleteByOid(String oid);

    Issue selectByOid(String oid);

    /**
     * 列表查询（筛选均可空；排序：未闭环在前，同列按创建时间倒序）。
     *
     * <p>{@code relatedType / relatedCode} = 按「关联对象」过滤：对象详情页（零组件 / 文档等）
     * 要列出"挂在这个对象上的问题"，而问题只存类型 + 编码（见 {@code ck_issue.related_type/related_code}）。
     */
    List<Issue> selectList(String tenantOid, String status, String severity, String owner,
                           String keyword, String relatedType, String relatedCode,
                           int limit, int offset);

    int countList(String tenantOid, String status, String severity, String owner, String keyword,
                  String relatedType, String relatedCode);

    /** 按状态计数（每行 {status, cnt}），供统计卡与闭环率用 */
    List<Map<String, Object>> countByStatus(String tenantOid);

    /**
     * 某产品（系列 / 型号）下的**未闭环**问题（OPEN / IN_PROGRESS / VERIFYING）。
     *
     * <p>排序：BLOCK 在前，再按创建时间倒序 —— 门的 ISSUE_OPEN 检查要让人第一眼看到阻塞项。
     */
    List<Issue> selectOpenByProduct(String tenantOid, String productOid);

    /** 当前租户已用的最大编号序号（ISS-#### 的 ####；空表返回 0） */
    int maxCodeSeq(String tenantOid);
}
