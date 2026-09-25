/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper.impl.postgresql;

import cn.ck.plm.resource.mapper.PackageSymbolMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link PackageSymbolMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlPackageSymbolMapper extends PackageSymbolMapper {

    String PLATFORM_OID = "'00000000-0000-0000-0000-000000000000'";

    /**
     * 封装 / 图符清单。
     *
     * <p>注意：返回 Map 时 MyBatis 的 mapUnderscoreToCamelCase 仅「去下划线且全小写」，
     * 因此所有字段统一使用带双引号的驼峰别名，保证前端能直接取值。
     */
    @Override
    @Select("<script>" +
            "SELECT p.oid, p.name, p.number, p.description, p.creator, " +
            "p.type_definition_code AS \"typeDefinitionCode\", " +
            "p.folder_oid AS \"folderOid\", p.container_oid AS \"containerOid\", " +
            "p.created_at AS \"createdAt\", " +
            // 最新迭代 oid：列表行只带主对象 oid 时，发起流程那边就无法记下"对象的哪一版"
            "i.oid AS \"iterationOid\", " +
            "i.checked_out AS \"checkedOut\", i.checked_out_by AS \"checkedOutBy\", " +
            "i.checked_out_comment AS \"checkedOutComment\", i.display_version AS \"displayVersion\", " +
            // 状态显示名：模板里的 display_name 在前（与迭代上的 code 配套），字典兜底，最后退回 code
            "i.status AS \"statusCode\", "
            + "COALESCE(tps.status_display_name, s.name, i.status) AS \"statusName\", " +
            "f.ipc_name AS \"ipcName\", f.mount_type AS \"mountType\", f.pad_count AS \"padCount\", " +
            "f.pitch_mm AS \"pitchMm\", f.body_size AS \"bodySize\", f.height_mm AS \"heightMm\", " +
            "f.ipc_compliant AS \"ipcCompliant\", f.eda_format AS \"footprintEdaFormat\", " +
            "sy.symbol_category AS \"symbolCategory\", sy.pin_count AS \"pinCount\", " +
            "sy.eda_format AS \"symbolEdaFormat\" " +
            "FROM ck_eng_document p " +
            "LEFT JOIN ck_eng_document_iteration i ON i.master_oid = p.oid AND i.latest = TRUE " +
            "LEFT JOIN ck_lifecycle_status s ON s.code = i.status " +
            "LEFT JOIN ck_lifecycle_template_state tps "
            + "ON tps.iteration_oid = i.lifecycle_template_iteration_oid AND tps.status_code = i.status " +
            "LEFT JOIN ck_ecad_footprint_ext f ON f.oid = p.oid " +
            "LEFT JOIN ck_ecad_symbol_ext sy ON sy.oid = p.oid " +
            "<where>" +
            " p.type_definition_code IN ('FOOTPRINT','SYMBOL')" +
            "<if test='typeCode != null and typeCode != \"\"'>" +
            " AND p.type_definition_code = #{typeCode}" +
            "</if>" +
            "<if test='folderOid != null and folderOid != \"\"'>" +
            " AND p.folder_oid = #{folderOid}" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (p.name ILIKE CONCAT('%',#{keyword},'%') OR p.number ILIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</where>" +
            "ORDER BY p.created_at DESC" +
            "</script>")
    List<Map<String, Object>> selectPackageSymbolItems(@Param("folderOid") String folderOid,
                                                       @Param("typeCode") String typeCode,
                                                       @Param("keyword") String keyword);

    @Override
    @Select("SELECT oid FROM ck_stage WHERE owner_oid = #{ownerOid} AND owner_type = 'LINE' " +
            "AND tenant_oid = " + PLATFORM_OID + " ORDER BY sort_order ASC, code ASC LIMIT 1")
    String selectResourceStageOid(@Param("ownerOid") String ownerOid);

    @Override
    @Insert("INSERT INTO ck_stage (oid, code, name, description, sort_order, owner_oid, owner_type, " +
            "show_on_dashboard, tenant_oid, created_at, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, '企业资源库-封装图符库归属阶段', 0, #{ownerOid}, 'LINE', TRUE, " +
            PLATFORM_OID + ", #{now}, #{now})")
    int insertResourceStage(@Param("oid") String oid,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("ownerOid") String ownerOid,
                            @Param("now") LocalDateTime now);
}
