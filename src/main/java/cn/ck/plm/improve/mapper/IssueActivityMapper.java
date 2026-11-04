/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.mapper;

import cn.ck.plm.improve.entity.IssueActivity;

import java.util.List;

/**
 * 问题处理记录 Mapper —— 只增不改：记录被改过就不叫记录了。
 *
 * <p>同样显式书写 {@code tenant_oid}（本表不在拦截器共享名单里）。
 */
public interface IssueActivityMapper {

    int insert(IssueActivity activity);

    /** 某条问题的完整历程（按时间正序） */
    List<IssueActivity> selectByIssue(String issueOid, String tenantOid);
}
