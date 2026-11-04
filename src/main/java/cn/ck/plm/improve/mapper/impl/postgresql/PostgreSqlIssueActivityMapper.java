/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.mapper.impl.postgresql;

import cn.ck.plm.improve.entity.IssueActivity;
import cn.ck.plm.improve.mapper.IssueActivityMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link IssueActivityMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlIssueActivityMapper extends IssueActivityMapper {

    String COLUMNS = "oid, issue_oid, action, from_status, to_status, comment, operator, "
            + "tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_issue_activity (" + COLUMNS + ") " +
            "VALUES (#{oid}, #{issueOid}, #{action}, #{fromStatus}, #{toStatus}, #{comment}, #{operator}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(IssueActivity activity);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_issue_activity " +
            "WHERE issue_oid = #{issueOid} AND tenant_oid = #{tenantOid} " +
            "ORDER BY created_at ASC, oid ASC")
    @Results(id = "issueActivityResult", value = {
            @Result(property = "oid",        column = "oid"),
            @Result(property = "issueOid",   column = "issue_oid"),
            @Result(property = "action",     column = "action"),
            @Result(property = "fromStatus", column = "from_status"),
            @Result(property = "toStatus",   column = "to_status"),
            @Result(property = "comment",    column = "comment"),
            @Result(property = "operator",   column = "operator"),
            @Result(property = "tenantOid",  column = "tenant_oid"),
            @Result(property = "creator",    column = "creator"),
            @Result(property = "createdAt",  column = "created_at"),
            @Result(property = "updater",    column = "updater"),
            @Result(property = "updatedAt",  column = "updated_at")
    })
    List<IssueActivity> selectByIssue(@Param("issueOid") String issueOid,
                                      @Param("tenantOid") String tenantOid);
}
