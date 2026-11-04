/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeNoticeIssue;

import java.util.List;

/**
 * 变更通告 ↔ 问题 关联表 Mapper（与 {@link ChangeRequestIssueMapper} 同构）。
 *
 * <p>本表不在租户拦截器共享名单里 → 每条 SQL 显式书写 {@code tenant_oid}。
 */
public interface ChangeNoticeIssueMapper {

    /** 批量建立关联 */
    int insertBatch(List<ChangeNoticeIssue> rows);

    /** 按通告清空关联 */
    int deleteByNoticeOid(String noticeOid, String tenantOid);

    /** 关联问题（编号 / 标题 / 状态） */
    List<ChangeIssueRef> selectIssues(String noticeOid, String tenantOid);

    /** 已关联的问题 oid（编辑回填） */
    List<String> selectIssueOids(String noticeOid, String tenantOid);
}
