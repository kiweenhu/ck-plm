/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.improve.mapper.impl.postgresql;

import cn.ck.plm.improve.mapper.IssueObjectLookupMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link IssueObjectLookupMapper} 的 PostgreSQL 实现。
 *
 * <p>关键字空 → 按更新时间倒序给最近 N 条（"打开下拉就有东西可选"）；
 * 关键字非空 → 编码或名称 {@code ILIKE}。租户条件由拦截器注入到主表别名上（SQL 里不写）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlIssueObjectLookupMapper extends IssueObjectLookupMapper {

    String PART_SQL = "<script>SELECT p.oid AS \"oid\", 'PART' AS \"entityType\", p.number AS \"code\", "
            + "p.name AS \"name\" FROM ck_part p"
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + " AND (p.number ILIKE '%' || #{keyword} || '%' OR p.name ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY p.updated_at DESC NULLS LAST LIMIT #{limit}</script>";

    String DOC_SQL = "<script>SELECT d.oid AS \"oid\", 'DOCUMENT' AS \"entityType\", d.number AS \"code\", "
            + "d.name AS \"name\" FROM ck_document d"
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + " AND (d.number ILIKE '%' || #{keyword} || '%' OR d.name ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY d.updated_at DESC NULLS LAST LIMIT #{limit}</script>";

    String ENG_SQL = "<script>SELECT e.oid AS \"oid\", 'ENG_DOCUMENT' AS \"entityType\", e.number AS \"code\", "
            + "e.name AS \"name\" FROM ck_eng_document e"
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + " AND (e.number ILIKE '%' || #{keyword} || '%' OR e.name ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY e.updated_at DESC NULLS LAST LIMIT #{limit}</script>";

    String LINE_SQL = "<script>SELECT l.oid AS \"oid\", 'PRODUCT_LINE' AS \"entityType\", l.code AS \"code\", "
            + "l.name AS \"name\" FROM ck_product_line l"
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + " AND (l.code ILIKE '%' || #{keyword} || '%' OR l.name ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY l.updated_at DESC NULLS LAST LIMIT #{limit}</script>";

    String MODEL_SQL = "<script>SELECT m.oid AS \"oid\", 'PRODUCT_MODEL' AS \"entityType\", m.code AS \"code\", "
            + "m.name AS \"name\" FROM ck_product_model m"
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + " AND (m.code ILIKE '%' || #{keyword} || '%' OR m.name ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY m.updated_at DESC NULLS LAST LIMIT #{limit}</script>";

    @Override
    @Select(PART_SQL)
    List<Map<String, Object>> searchParts(@Param("keyword") String keyword, @Param("limit") int limit);

    @Override
    @Select(DOC_SQL)
    List<Map<String, Object>> searchDocuments(@Param("keyword") String keyword, @Param("limit") int limit);

    @Override
    @Select(ENG_SQL)
    List<Map<String, Object>> searchEngDocuments(@Param("keyword") String keyword, @Param("limit") int limit);

    @Override
    @Select(LINE_SQL)
    List<Map<String, Object>> searchProductLines(@Param("keyword") String keyword, @Param("limit") int limit);

    @Override
    @Select(MODEL_SQL)
    List<Map<String, Object>> searchProductModels(@Param("keyword") String keyword, @Param("limit") int limit);
}
