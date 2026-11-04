/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeActivity;

import java.util.List;

/**
 * 变更操作记录 Mapper（时间线）。
 *
 * <p>本表不在 {@code TenantStatementInterceptor} 的共享名单里 → 每条 SQL 都<b>显式</b>
 * 书写 {@code tenant_oid}。
 */
public interface ChangeActivityMapper {

    int insert(ChangeActivity activity);

    /** 某张变更单的时间线（按时间正序，前端直接渲染） */
    List<ChangeActivity> selectByOwner(String changeType, String changeOid);

    /** 主单删除时一并清理 */
    int deleteByOwner(String changeType, String changeOid);
}
