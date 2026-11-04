/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeNoticeIssue;
import cn.ck.plm.change.mapper.ChangeNoticeIssueMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ChangeNoticeIssueMapper} 的 PostgreSQL 实现（与请求侧同构）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeNoticeIssueMapper extends ChangeNoticeIssueMapper {

    @Override
    @Insert("<script>INSERT INTO ck_change_notice_issue (oid, notice_oid, issue_oid, tenant_oid, creator) VALUES "
            + "<foreach collection='rows' item='r' separator=','>"
            + "(#{r.oid}, #{r.noticeOid}, #{r.issueOid}, #{r.tenantOid}, #{r.creator})"
            + "</foreach></script>")
    int insertBatch(@Param("rows") List<ChangeNoticeIssue> rows);

    @Override
    @Delete("DELETE FROM ck_change_notice_issue WHERE notice_oid = #{noticeOid} AND tenant_oid = #{tenantOid}")
    int deleteByNoticeOid(@Param("noticeOid") String noticeOid, @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT i.oid AS oid, i.code AS code, i.name AS name, i.status AS status "
            + "FROM ck_change_notice_issue r "
            + "JOIN ck_issue i ON i.oid = r.issue_oid "
            + "WHERE r.notice_oid = #{noticeOid} AND r.tenant_oid = #{tenantOid} "
            + "ORDER BY i.code")
    List<ChangeIssueRef> selectIssues(@Param("noticeOid") String noticeOid, @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT r.issue_oid FROM ck_change_notice_issue r "
            + "JOIN ck_issue i ON i.oid = r.issue_oid "
            + "WHERE r.notice_oid = #{noticeOid} AND r.tenant_oid = #{tenantOid} "
            + "ORDER BY i.code")
    List<String> selectIssueOids(@Param("noticeOid") String noticeOid, @Param("tenantOid") String tenantOid);
}
