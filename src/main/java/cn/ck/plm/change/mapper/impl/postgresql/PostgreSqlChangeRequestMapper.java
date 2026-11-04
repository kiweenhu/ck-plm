/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.mapper.ChangeRequestMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link ChangeRequestMapper} 的 PostgreSQL 实现。
 *
 * <p>每条语句都<b>显式</b>写明 {@code tenant_oid}：语义不依赖拦截器注入，
 * 固定为"只本租户"。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeRequestMapper extends ChangeRequestMapper {

    String COLUMNS = "oid, code, name, description, attachment_oid, change_type, priority, reason, proposal, status, "
            + "source_issue_oid, product_oid, product_type, owner, requester, due_date, "
            + "accepted_at, closed_at, process_instance_id, change_level, cert_impact, customer_impact, "
            + "inventory_impact, tenant_oid, creator, created_at, updater, updated_at";

    /**
     * 查询用列：实体列 + 所属容器名。
     *
     * <p>product_oid 可能指向系列也可能指向型号，用两次索引点查 + COALESCE 取名，
     * 比 LEFT JOIN 两张表再合并可读，且详情/列表共用一处定义。
     *
     * <p>不能塞进 {@link #COLUMNS}：那个常量也用于 INSERT 的列名列表。
     *
     * <p>{@code notice_code} 同理（标量子查询）：通告表单的「来源变更请求」下拉靠它
     * 把已派发过通告的请求置灰。已取消的通告不算占用 —— 单据作废后请求可重新派发；
     * 一张请求理论上可派生多张通告，这里只取最早一张用来提示"被谁占了"。
     */
    String SELECT_COLUMNS = COLUMNS + ", COALESCE("
            + "(SELECT pm.name FROM ck_product_model pm WHERE pm.oid = ck_change_request.product_oid), "
            + "(SELECT pl.name FROM ck_product_line  pl WHERE pl.oid = ck_change_request.product_oid)"
            + ") AS product_name"
            + ", (SELECT n.code FROM ck_change_notice n"
            + "   WHERE n.request_oid = ck_change_request.oid AND n.status != 'CANCELLED'"
            + "   ORDER BY n.created_at LIMIT 1) AS notice_code";

    @Override
    @Insert("INSERT INTO ck_change_request (" + COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{attachmentOid}, #{changeType}, #{priority}, #{reason}, #{proposal}, " +
            "#{status}, #{sourceIssueOid}, #{productOid}, #{productType}, #{owner}, #{requester}, #{dueDate}, " +
            "#{acceptedAt}, #{closedAt}, #{processInstanceId}, #{changeLevel}, #{certImpact}, #{customerImpact}, " +
            "#{inventoryImpact}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ChangeRequest request);

    @Override
    @Update("UPDATE ck_change_request SET name = #{name}, description = #{description}, " +
            "attachment_oid = #{attachmentOid}, " +
            "change_type = #{changeType}, priority = #{priority}, reason = #{reason}, proposal = #{proposal}, " +
            "status = #{status}, source_issue_oid = #{sourceIssueOid}, product_oid = #{productOid}, " +
            "product_type = #{productType}, owner = #{owner}, requester = #{requester}, due_date = #{dueDate}, " +
            "accepted_at = #{acceptedAt}, closed_at = #{closedAt}, process_instance_id = #{processInstanceId}, " +
            "change_level = #{changeLevel}, cert_impact = #{certImpact}, customer_impact = #{customerImpact}, " +
            "inventory_impact = #{inventoryImpact}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ChangeRequest request);

    @Override
    @Update("UPDATE ck_change_request SET process_instance_id = #{processInstanceId}, " +
            "updater = #{updater}, updated_at = now() WHERE oid = #{oid}")
    int updateProcessInstanceId(@Param("oid") String oid,
                                @Param("processInstanceId") String processInstanceId,
                                @Param("updater") String updater);

    @Override
    @Delete("DELETE FROM ck_change_request WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + SELECT_COLUMNS + " FROM ck_change_request WHERE oid = #{oid}")
    @Results(id = "changeRequestResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "attachmentOid",  column = "attachment_oid"),
            @Result(property = "changeType",     column = "change_type"),
            @Result(property = "priority",       column = "priority"),
            @Result(property = "reason",         column = "reason"),
            @Result(property = "proposal",       column = "proposal"),
            @Result(property = "status",         column = "status"),
            @Result(property = "sourceIssueOid", column = "source_issue_oid"),
            @Result(property = "productOid",     column = "product_oid"),
            @Result(property = "productType",    column = "product_type"),
            @Result(property = "productName",    column = "product_name"),
            @Result(property = "noticeCode",     column = "notice_code"),
            @Result(property = "owner",          column = "owner"),
            @Result(property = "requester",      column = "requester"),
            @Result(property = "dueDate",        column = "due_date"),
            @Result(property = "acceptedAt",     column = "accepted_at"),
            @Result(property = "closedAt",       column = "closed_at"),
            @Result(property = "processInstanceId", column = "process_instance_id"),
            @Result(property = "changeLevel",    column = "change_level"),
            @Result(property = "certImpact",     column = "cert_impact"),
            @Result(property = "customerImpact", column = "customer_impact"),
            @Result(property = "inventoryImpact", column = "inventory_impact"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    ChangeRequest selectByOid(@Param("oid") String oid);

    @Override
    @Select("<script>" +
            "SELECT " + SELECT_COLUMNS + " FROM ck_change_request " +
            "WHERE tenant_oid = #{tenantOid} " +
            "<if test='status != null and status != \"\"'>  AND status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%' " +
            "       OR reason ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY CASE WHEN status IN ('CLOSED', 'CANCELLED') THEN 1 ELSE 0 END, created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @ResultMap("changeRequestResult")
    List<ChangeRequest> selectList(@Param("tenantOid") String tenantOid,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_change_request WHERE tenant_oid = #{tenantOid} " +
            "<if test='status != null and status != \"\"'>  AND status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%' " +
            "       OR reason ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "</script>")
    int countList(@Param("tenantOid") String tenantOid,
                  @Param("status") String status,
                  @Param("keyword") String keyword);

    @Override
    @Select("SELECT status, COUNT(*) AS cnt FROM ck_change_request " +
            "WHERE tenant_oid = #{tenantOid} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COALESCE(MAX(SUBSTRING(code FROM 5)::INTEGER), 0) FROM ck_change_request " +
            "WHERE tenant_oid = #{tenantOid} AND code ~ '^ECR-[0-9]+$'")
    int maxCodeSeq(@Param("tenantOid") String tenantOid);
}
