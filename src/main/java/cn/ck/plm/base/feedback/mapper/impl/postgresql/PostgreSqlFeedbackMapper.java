/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.mapper.impl.postgresql;

import cn.ck.plm.base.feedback.entity.Feedback;
import cn.ck.plm.base.feedback.mapper.FeedbackMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link FeedbackMapper} 的 PostgreSQL 实现。
 *
 * <p>ck_feedback 登记在 TenantStatementInterceptor 的 SHARED 名单里，拦截器不会注入
 * tenant_oid 条件，所以这里的 SQL 不需要（也不能）带租户过滤。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlFeedbackMapper extends FeedbackMapper {

    @Override
    @Insert("INSERT INTO ck_feedback (oid, category, title, content, contact, page_url, "
            + "tenant_id, tenant_name, reporter_oid, reporter_name, client_info, status, "
            + "creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{category}, #{title}, #{content}, #{contact}, #{pageUrl}, "
            + "#{tenantId}, #{tenantName}, #{reporterOid}, #{reporterName}, #{clientInfo}, #{status}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Feedback feedback);

    @Override
    @Select("SELECT oid, category, title, content, contact, page_url, tenant_id, tenant_name, "
            + "reporter_oid, reporter_name, client_info, status, reply, reply_at, replier_name, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_feedback WHERE oid = #{oid}")
    @Results(id = "feedbackResult", value = {
            @Result(property = "oid",          column = "oid"),
            @Result(property = "category",     column = "category"),
            @Result(property = "title",        column = "title"),
            @Result(property = "content",      column = "content"),
            @Result(property = "contact",      column = "contact"),
            @Result(property = "pageUrl",      column = "page_url"),
            @Result(property = "tenantId",     column = "tenant_id"),
            @Result(property = "tenantName",   column = "tenant_name"),
            @Result(property = "reporterOid",  column = "reporter_oid"),
            @Result(property = "reporterName", column = "reporter_name"),
            @Result(property = "clientInfo",   column = "client_info"),
            @Result(property = "status",       column = "status"),
            @Result(property = "reply",        column = "reply"),
            @Result(property = "replyAt",      column = "reply_at"),
            @Result(property = "replierName",  column = "replier_name"),
            @Result(property = "creator",      column = "creator"),
            @Result(property = "createdAt",    column = "created_at"),
            @Result(property = "updater",      column = "updater"),
            @Result(property = "updatedAt",    column = "updated_at")
    })
    Feedback selectByOid(@Param("oid") String oid);

    /**
     * 列表刻意不查 content（可能内嵌 base64 截图）：正文由 {@link #selectByOid} 单条取。
     * 复用同一个 ResultMap 即可 —— 没查的列自然是 null，不会被映射成脏值。
     */
    @Override
    @Select("<script>"
            + "SELECT oid, category, title, contact, page_url, tenant_id, tenant_name, "
            + "reporter_oid, reporter_name, client_info, status, reply, reply_at, replier_name, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_feedback "
            + "<where><if test='status != null and status != \"\"'>status = #{status}</if></where> "
            + "ORDER BY created_at DESC LIMIT #{limit}"
            + "</script>")
    @ResultMap("feedbackResult")
    List<Feedback> selectRecent(@Param("limit") int limit, @Param("status") String status);

    /** 本人「我的反馈」：同样不查 content（正文只在详情里取） */
    @Override
    @Select("SELECT oid, category, title, contact, page_url, tenant_id, tenant_name, "
            + "reporter_oid, reporter_name, client_info, status, reply, reply_at, replier_name, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_feedback WHERE reporter_oid = #{reporterOid} "
            + "ORDER BY created_at DESC LIMIT #{limit}")
    @ResultMap("feedbackResult")
    List<Feedback> selectByReporter(@Param("reporterOid") String reporterOid, @Param("limit") int limit);

    @Override
    @Select("SELECT COUNT(*) FROM ck_feedback WHERE status = 'NEW'")
    int countUnprocessed();

    @Override
    @Update("UPDATE ck_feedback SET reply = #{reply}, reply_at = #{replyAt}, "
            + "replier_name = #{replierName}, status = #{status}, "
            + "updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int updateReply(Feedback feedback);
}
