/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.dto.ChangeIssueRef;

import java.util.List;

/**
 * 变更单（ECR / ECN）挑问题时的统一查法。
 *
 * <h3>为什么单独一个 Mapper</h3>
 * <p>变更请求与变更通告都要"只挑未关闭的问题"，这条规则只能有一处实现 ——
 * 两边各写一遍，迟早会出现"请求里能选、通告里能选"的不一致。
 *
 * <h3>为什么直接查 ck_issue 而不复用 Improve 模块的 Service</h3>
 * <p>{@code IssueService#list} 的筛选是给"问题台账"页用的（按状态/严重度/责任人分页），
 * 没有"排除已关闭"这个口径；为了两个下拉去改公共接口的签名，影响面比这里写一条 SQL 大得多。
 */
public interface ChangeIssueLookupMapper {

    /**
     * 可关联的问题：状态为 OPEN / IN_PROGRESS / VERIFYING。
     *
     * <p>CLOSED 与 CANCELLED 都排除 —— 已闭环或已作废的问题不该再驱动一次变更。
     */
    List<ChangeIssueRef> selectOpenIssues(String keyword, String tenantOid, int limit);

    /**
     * 这批 oid 里<b>已关闭 / 已取消</b>的问题（用于校验用户手工提交的选择）。
     *
     * <p>返回而不是抛异常：服务层要拿编号拼出"ISS-0001 已关闭"这种能直接给人看的话。
     */
    List<ChangeIssueRef> selectClosedIssues(List<String> issueOids, String tenantOid);
}
