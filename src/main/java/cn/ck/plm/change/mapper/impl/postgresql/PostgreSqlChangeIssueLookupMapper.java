/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.mapper.ChangeIssueLookupMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ChangeIssueLookupMapper} 的 PostgreSQL 实现。
 *
 * <p>显式写 {@code tenant_oid}：ck_issue 不在共享名单里，语义不依赖拦截器注入。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeIssueLookupMapper extends ChangeIssueLookupMapper {

    @Override
    @Select("<script>"
            + "SELECT i.oid AS oid, i.code AS code, i.name AS name, i.status AS status "
            + "FROM ck_issue i "
            + "WHERE i.tenant_oid = #{tenantOid} "
            + "  AND i.status IN ('OPEN', 'IN_PROGRESS', 'VERIFYING') "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "  AND (i.code ILIKE '%' || #{keyword} || '%' OR i.name ILIKE '%' || #{keyword} || '%') "
            + "</if>"
            + "ORDER BY i.code "
            + "LIMIT #{limit}"
            + "</script>")
    List<ChangeIssueRef> selectOpenIssues(@Param("keyword") String keyword,
                                          @Param("tenantOid") String tenantOid,
                                          @Param("limit") int limit);

    @Override
    @Select("<script>"
            + "SELECT i.oid AS oid, i.code AS code, i.name AS name, i.status AS status "
            + "FROM ck_issue i "
            + "WHERE i.tenant_oid = #{tenantOid} "
            + "  AND i.status IN ('CLOSED', 'CANCELLED') "
            + "  AND i.oid IN "
            + "<foreach collection='issueOids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "ORDER BY i.code"
            + "</script>")
    List<ChangeIssueRef> selectClosedIssues(@Param("issueOids") List<String> issueOids,
                                            @Param("tenantOid") String tenantOid);
}
