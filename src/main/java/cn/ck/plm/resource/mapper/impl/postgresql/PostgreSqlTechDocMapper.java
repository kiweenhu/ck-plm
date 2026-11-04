/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper.impl.postgresql;

import cn.ck.plm.resource.mapper.TechDocMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link TechDocMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>返回 Map 时 MyBatis 的 mapUnderscoreToCamelCase 只「去下划线且全小写」，
 * 所以字段一律用带双引号的驼峰别名 —— 与 {@link PostgreSqlPackageSymbolMapper} 同一约定。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTechDocMapper extends TechDocMapper {

    @Override
    @Select("<script>" +
            "SELECT d.oid, d.name, d.number, d.description, d.creator, " +
            "d.type_definition_code AS \"typeDefinitionCode\", " +
            "d.folder_oid AS \"folderOid\", d.container_oid AS \"containerOid\", " +
            "d.created_at AS \"createdAt\", " +
            // 最新迭代：列表行要能看出"这份文档当前是哪一版、谁检出、什么状态"
            "i.oid AS \"iterationOid\", " +
            "i.checked_out AS \"checkedOut\", i.checked_out_by AS \"checkedOutBy\", " +
            "i.checked_out_comment AS \"checkedOutComment\", i.display_version AS \"displayVersion\", " +
            "i.status AS \"statusCode\", " +
            "COALESCE(tps.status_display_name, s.name, i.status) AS \"statusName\", " +
            // 主文件：技术文档的"本体"（PDF / Word / Excel ...），列表直接给出文件名与大小
            "f.oid AS \"ckfileOid\", f.file_name AS \"fileName\", f.file_size AS \"fileSize\", " +
            "f.mime_type AS \"mimeType\" " +
            "FROM ck_document d " +
            // delete_mark 只在迭代表上有：作废的迭代不能被当成最新版展示
            "LEFT JOIN ck_document_iteration i ON i.master_oid = d.oid AND i.latest = TRUE AND i.delete_mark = FALSE " +
            // 状态字典/模板关联按租户限定，否则 JOIN 会行膨胀或取到别家租户的同名状态
            "LEFT JOIN ck_lifecycle_status s ON s.code = i.status AND s.tenant_oid = d.tenant_oid " +
            "LEFT JOIN ck_lifecycle_template_state tps " +
            "  ON tps.iteration_oid = i.lifecycle_template_iteration_oid " +
            " AND tps.status_code = i.status AND tps.tenant_oid = d.tenant_oid " +
            "LEFT JOIN ck_file f ON f.oid = i.ckfile_oid " +
            "<where>" +
            // 只有这一个条件是不可省的：Document 是通用类型，不按容器收口会列出全系统的文档
            " d.container_oid = #{containerOid}" +
            "<if test='folderOid != null and folderOid != \"\"'>" +
            " AND d.folder_oid = #{folderOid}" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (d.name ILIKE CONCAT('%',#{keyword},'%')" +
            "   OR d.number ILIKE CONCAT('%',#{keyword},'%')" +
            "   OR f.file_name ILIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</where>" +
            "ORDER BY d.created_at DESC" +
            "</script>")
    List<Map<String, Object>> selectTechDocItems(@Param("containerOid") String containerOid,
                                                 @Param("folderOid") String folderOid,
                                                 @Param("keyword") String keyword);

    @Override
    @Select("SELECT oid FROM ck_stage WHERE owner_oid = #{ownerOid} AND owner_type = 'LINE' " +
            "AND tenant_oid = #{tenantOid} ORDER BY sort_order ASC, code ASC LIMIT 1")
    String selectResourceStageOid(@Param("ownerOid") String ownerOid, @Param("tenantOid") String tenantOid);

    @Override
    @Insert("INSERT INTO ck_stage (oid, code, name, description, sort_order, owner_oid, owner_type, " +
            "show_on_dashboard, tenant_oid, created_at, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, '企业资源库-技术文档知识库归属阶段', 0, #{ownerOid}, 'LINE', TRUE, " +
            "#{tenantOid}, #{now}, #{now})")
    int insertResourceStage(@Param("oid") String oid,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("ownerOid") String ownerOid,
                            @Param("tenantOid") String tenantOid,
                            @Param("now") LocalDateTime now);
}
