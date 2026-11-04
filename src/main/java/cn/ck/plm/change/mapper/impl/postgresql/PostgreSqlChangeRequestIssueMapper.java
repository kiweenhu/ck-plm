/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeRequestIssue;
import cn.ck.plm.change.mapper.ChangeRequestIssueMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ChangeRequestIssueMapper} 的 PostgreSQL 实现。
 *
 * <p>每条语句都显式写明 {@code tenant_oid}：本表不在拦截器共享名单里，
 * 语义不依赖注入，固定为"只本租户"。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeRequestIssueMapper extends ChangeRequestIssueMapper {

    /**
     * 批量插入关联行。
     *
     * <p>用 {@code VALUES (...),(...)} 一次发一条：一张变更单通常挂三五个问题，
     * 逐个 insert 只是徒增往返；{@code oid} 由服务层生成（与全项目一致，不依赖 DB 函数）。
     */
    @Override
    @Insert("<script>INSERT INTO ck_change_request_issue (oid, request_oid, issue_oid, tenant_oid, creator) VALUES "
            + "<foreach collection='rows' item='r' separator=','>"
            + "(#{r.oid}, #{r.requestOid}, #{r.issueOid}, #{r.tenantOid}, #{r.creator})"
            + "</foreach></script>")
    int insertBatch(@Param("rows") List<ChangeRequestIssue> rows);

    @Override
    @Delete("DELETE FROM ck_change_request_issue WHERE request_oid = #{requestOid} AND tenant_oid = #{tenantOid}")
    int deleteByRequestOid(@Param("requestOid") String requestOid, @Param("tenantOid") String tenantOid);

    /**
     * 关联问题。{@code JOIN ck_issue} 取编号 / 标题 / 状态 ——
     * 问题被删时外键级联会一并清掉关联，不会留下指向空洞的引用。
     */
    @Override
    @Select("SELECT i.oid AS oid, i.code AS code, i.name AS name, i.status AS status "
            + "FROM ck_change_request_issue r "
            + "JOIN ck_issue i ON i.oid = r.issue_oid "
            + "WHERE r.request_oid = #{requestOid} AND r.tenant_oid = #{tenantOid} "
            + "ORDER BY i.code")
    List<ChangeIssueRef> selectIssues(@Param("requestOid") String requestOid, @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT r.issue_oid FROM ck_change_request_issue r "
            + "JOIN ck_issue i ON i.oid = r.issue_oid "
            + "WHERE r.request_oid = #{requestOid} AND r.tenant_oid = #{tenantOid} "
            + "ORDER BY i.code")
    List<String> selectIssueOids(@Param("requestOid") String requestOid, @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT r.issue_oid FROM ck_change_request_issue r "
            + "JOIN ck_issue i ON i.oid = r.issue_oid "
            + "WHERE r.request_oid = #{requestOid} AND r.tenant_oid = #{tenantOid} "
            + "  AND i.status IN ('OPEN', 'IN_PROGRESS', 'VERIFYING') "
            + "ORDER BY i.code")
    List<String> selectOpenIssueOids(@Param("requestOid") String requestOid, @Param("tenantOid") String tenantOid);
}
