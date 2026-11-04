/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeRequest;

import java.util.List;
import java.util.Map;

/**
 * 变更请求 Mapper。
 *
 * <p>本表不在 {@code TenantStatementInterceptor} 的共享名单里 → 每条 SQL 都<b>显式</b>
 * 书写 {@code tenant_oid}（语义不依赖拦截器注入，固定为"只本租户"）。
 */
public interface ChangeRequestMapper {

    int insert(ChangeRequest request);

    /** 全字段更新（patch 语义由服务层合并后传入） */
    int update(ChangeRequest request);

    /**
     * 只写流程实例 id。
     *
     * <p>发起评审时状态由流程里的「设置状态」服务节点写：先把单据落库、再拉起流程，
     * 流程跑到第一个等待点时状态已经变了 —— 此时再用 {@link #update(ChangeRequest)}
     * 整行回写，会把旧状态原样冲回去。故流程实例 id 单独一条语句补写。
     */
    int updateProcessInstanceId(String oid, String processInstanceId, String updater);

    int deleteByOid(String oid);

    ChangeRequest selectByOid(String oid);

    /** 列表查询（筛选均可空；未结束在前，同列按创建时间倒序） */
    List<ChangeRequest> selectList(String tenantOid, String status, String keyword, int limit, int offset);

    int countList(String tenantOid, String status, String keyword);

    /** 按状态计数（每行 {status, cnt}），供页头的状态概览用 */
    List<Map<String, Object>> countByStatus(String tenantOid);

    /** 当前租户已用的最大编号序号（ECR-#### 的 ####；空表返回 0） */
    int maxCodeSeq(String tenantOid);
}
