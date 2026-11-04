/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeNotice;

import java.util.List;
import java.util.Map;

/**
 * 变更通告 Mapper。
 *
 * <p>本表不在 {@code TenantStatementInterceptor} 的共享名单里 → 每条 SQL 都<b>显式</b>
 * 书写 {@code tenant_oid}。
 */
public interface ChangeNoticeMapper {

    int insert(ChangeNotice notice);

    /** 全字段更新（patch 语义由服务层合并后传入） */
    int update(ChangeNotice notice);

    int deleteByOid(String oid);

    ChangeNotice selectByOid(String oid);

    /** 列表查询（筛选均可空） */
    List<ChangeNotice> selectList(String tenantOid, String status, String requestOid,
                                  String keyword, int limit, int offset);

    int countList(String tenantOid, String status, String requestOid, String keyword);

    /** 按状态计数（每行 {status, cnt}），供统计卡用 */
    List<Map<String, Object>> countByStatus(String tenantOid);

    /**
     * 某变更请求（ECR）下仍未关闭的变更通告（子流程）。
     *
     * <p>"未关闭" = 非 {@code CLOSED} / {@code CANCELLED}。ECR 的关闭动作以此为门槛：
     * 子流程没走完，父单不该先关 —— 否则改了什么都说不清。
     */
    List<ChangeNotice> selectOpenByRequest(String requestOid, String tenantOid);

    /**
     * 某变更请求（ECR）下已派生的变更通告数量（不分状态）。
     *
     * <p>0 = 这张请求从来没派发过执行单。ECR 的关闭动作以此为前置门槛：
     * 没有通告就意味着"怎么改"从未落到具体对象/任务上，关闭等于跳过整个实施过程。
     */
    int countByRequest(String requestOid, String tenantOid);

    /** 当前租户已用的最大编号序号（ECN-#### 的 ####；空表返回 0） */
    int maxCodeSeq(String tenantOid);
}
