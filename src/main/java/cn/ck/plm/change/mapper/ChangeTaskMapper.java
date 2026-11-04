/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeTask;

import java.util.List;
import java.util.Map;

/**
 * 变更任务 Mapper。
 *
 * <p>本表不在 {@code TenantStatementInterceptor} 的共享名单里 → 每条 SQL 都<b>显式</b>
 * 书写 {@code tenant_oid}。
 */
public interface ChangeTaskMapper {

    int insert(ChangeTask task);

    int update(ChangeTask task);

    int deleteByOid(String oid);

    ChangeTask selectByOid(String oid);

    /** 某张变更通告下的全部任务（按创建时间正序） */
    List<ChangeTask> selectByNotice(String noticeOid);

    /** 我的任务（assignee 为空时返回租户下全部；keyword 匹配编号/标题） */
    List<ChangeTask> selectMine(String tenantOid, String assignee, String status, String keyword,
                                int limit, int offset);

    int countMine(String tenantOid, String assignee, String status, String keyword);

    /**
     * 按状态计数（每行 {status, cnt}），供页头的状态概览用。
     *
     * <p>{@code assignee} 为空 = 租户下全部任务（"只看我的"未勾选时的口径）。
     */
    List<Map<String, Object>> countByStatus(String tenantOid, String assignee);

    /** 当前租户已用的最大编号序号（ECT-#### 的 ####；空表返回 0） */
    int maxCodeSeq(String tenantOid);

    /**
     * 某变更通告（ECN）下仍未完成的任务（子流程）。
     *
     * <p>"未完成" = 非 {@code COMPLETED}。ECN 的关闭动作以此为门槛：
     * 任务没验收完，通告不该先关。任务没有"取消"状态，故只有一种终态。
     */
    List<ChangeTask> selectOpenByNotice(String noticeOid);
}
