/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeRequestIssue;

import java.util.List;

/**
 * 变更请求 ↔ 问题 关联表 Mapper。
 *
 * <p>与 {@link ChangeRequestMapper} 同一口径：本表不在租户拦截器共享名单里，
 * 每条 SQL 都<b>显式</b>书写 {@code tenant_oid}。
 */
public interface ChangeRequestIssueMapper {

    /** 批量建立关联（调用方保证同一批里 issueOid 不重复） */
    int insertBatch(List<ChangeRequestIssue> rows);

    /** 按变更请求清空关联（编辑时先删后建；tenant_oid 防跨租户误删） */
    int deleteByRequestOid(String requestOid, String tenantOid);

    /** 关联问题列表（带编号 / 标题 / 状态，供详情展示） */
    List<ChangeIssueRef> selectIssues(String requestOid, String tenantOid);

    /** 已关联的问题 oid（编辑回填用；顺序按问题编号） */
    List<String> selectIssueOids(String requestOid, String tenantOid);

    /**
     * 该请求关联的、<b>仍未关闭</b>的问题 oid —— ECR 转 ECN 时带过去的那一份。
     *
     * <p>带入时按"未关闭"过滤而不是报错：请求是几个月前提的，期间某个问题已经闭环很正常，
     * 不该因此卡住一张新通告。
     */
    List<String> selectOpenIssueOids(String requestOid, String tenantOid);
}
