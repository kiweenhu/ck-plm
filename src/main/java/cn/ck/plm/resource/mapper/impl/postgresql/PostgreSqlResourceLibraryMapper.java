/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper.impl.postgresql;

import cn.ck.plm.resource.mapper.ResourceLibraryMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link ResourceLibraryMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>资源库节点归属阶段（{@code ck_stage}，owner_type='LINE'）**按租户隔离**
 * （2026-10-11 由"硬编码平台 oid"改为显式租户）：随 platform-base 包按租户各建一份，
 * 与容器（{@code ck_resource_container}）同生同灭。改前查/插都写平台，租户装包写不进本租户、
 * 读取也靠显式 SQL 撑着，同一张表两套口径。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlResourceLibraryMapper extends ResourceLibraryMapper {

    // ==================== 分类绑定配置 ====================

    @Override
    @Select("SELECT root_classification_oid FROM ck_library_cls_config " +
            "WHERE tenant_oid = #{tenantOid} AND COALESCE(resource_code, '') = COALESCE(#{resourceCode}, '') " +
            "ORDER BY updated_at DESC NULLS LAST LIMIT 1")
    String selectBindingRootOid(@Param("tenantOid") String tenantOid,
                                @Param("resourceCode") String resourceCode);

    @Override
    @Select("SELECT resource_code, root_classification_oid FROM ck_library_cls_config WHERE tenant_oid = #{tenantOid}")
    List<Map<String, Object>> selectAllBindingRootOids(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_library_cls_config " +
            "WHERE tenant_oid = #{tenantOid} AND COALESCE(resource_code, '') = COALESCE(#{resourceCode}, '')")
    int countBinding(@Param("tenantOid") String tenantOid,
                     @Param("resourceCode") String resourceCode);

    @Override
    @Insert("INSERT INTO ck_library_cls_config (oid, tenant_oid, resource_code, resource_node_oid, root_classification_oid, created_at, updated_at) " +
            "VALUES (#{oid}, #{tenantOid}, #{resourceCode}, #{resourceNodeOid}, #{rootClassificationOid}, #{now}, #{now})")
    int insertBinding(@Param("oid") String oid,
                      @Param("tenantOid") String tenantOid,
                      @Param("resourceCode") String resourceCode,
                      @Param("resourceNodeOid") String resourceNodeOid,
                      @Param("rootClassificationOid") String rootClassificationOid,
                      @Param("now") LocalDateTime now);

    @Override
    @Update("UPDATE ck_library_cls_config SET root_classification_oid = #{rootClassificationOid}, updated_at = #{now} " +
            "WHERE tenant_oid = #{tenantOid} AND COALESCE(resource_code, '') = COALESCE(#{resourceCode}, '')")
    int updateBinding(@Param("tenantOid") String tenantOid,
                      @Param("resourceCode") String resourceCode,
                      @Param("rootClassificationOid") String rootClassificationOid,
                      @Param("now") LocalDateTime now);

    // ==================== 资源库节点归属阶段（按租户隔离） ====================

    @Override
    @Select("SELECT oid FROM ck_stage WHERE owner_oid = #{ownerOid} AND owner_type = 'LINE' " +
            "AND tenant_oid = #{tenantOid} ORDER BY sort_order ASC, code ASC LIMIT 1")
    String selectResourceStageOid(@Param("ownerOid") String ownerOid, @Param("tenantOid") String tenantOid);

    @Override
    @Insert("INSERT INTO ck_stage (oid, code, name, description, sort_order, owner_oid, owner_type, show_on_dashboard, tenant_oid, created_at, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, '企业资源库-资源子库归属阶段', 0, #{ownerOid}, 'LINE', TRUE, #{tenantOid}, #{now}, #{now})")
    int insertResourceStage(@Param("oid") String oid,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("ownerOid") String ownerOid,
                            @Param("tenantOid") String tenantOid,
                            @Param("now") LocalDateTime now);

    // ==================== 资源库清单（按对象软类型：ELECTRONIC / STD_PART / GEN_PART） ====================

    /**
     * 注意：本查询返回 Map，MyBatis 对 Map 的 mapUnderscoreToCamelCase
     * 仅「去掉下划线且全小写」（cls_oid → clsoid），并非驼峰。
     * 因此这里统一用带双引号的驼峰别名，保证前端能直接以 clsOid / checkedOut / statusCode 取值。
     *
     * <p><b>statusName 必须是显示名，不是 code</b>：这里曾经写的是
     * {@code i.status AS "statusName"}（把 code 当名字返回），界面于是直接显示
     * {@code WORKING} / {@code DRAFT} —— 用户看不懂，而这恰恰是"列显示名"的最小要求。
     * 显示名的权威来源是<b>该对象所用生命周期模板</b>里的状态定义
     * （{@code ck_lifecycle_template_state}：code 与 display_name 配套写下，和迭代上存的 code 天然对得上）；
     * 全局状态字典（{@code ck_lifecycle_status}）只作兜底 —— 各租户状态词未必一致、也可能没有该 code。
     * 都取不到时退回 code，宁可显示 code 也不显示空白。
     */
    @Override
    @Select("<script>" +
            "SELECT p.oid, p.name, p.number, p.description, p.creator, " +
            "p.cls_oid AS \"clsOid\", p.type_definition_code AS \"typeDefinitionCode\", " +
            "p.container_oid AS \"containerOid\", p.stage_oid AS \"stageOid\", " +
            "p.created_at AS \"createdAt\", " +
            // 最新迭代 oid：列表行只带主对象 oid 时，发起流程那边就无法记下"对象的哪一版"
            "i.oid AS \"iterationOid\", " +
            "i.checked_out AS \"checkedOut\", i.checked_out_by AS \"checkedOutBy\", " +
            "i.checked_out_comment AS \"checkedOutComment\", i.display_version AS \"displayVersion\", " +
            "i.status AS \"statusCode\", " +
            // 2026-10-11 起生命周期状态/模板关联按租户隔离：这两处 JOIN/子查询必须同时限定本租户，
            // 否则平台与各租户各有同 code 的行 → 行膨胀 / LIMIT 1 取到别家的显示名
            "COALESCE(ls.status_display_name, "
            + "(SELECT COALESCE(d.display_name, d.name) FROM ck_lifecycle_status d "
            + "WHERE d.code = i.status AND d.tenant_oid = p.tenant_oid LIMIT 1), "
            + "i.status) AS \"statusName\" " +
            "FROM ck_part p " +
            "LEFT JOIN ck_part_iteration i ON i.master_oid = p.oid AND i.latest = TRUE " +
            "LEFT JOIN ck_lifecycle_template_state ls "
            + "ON ls.iteration_oid = i.lifecycle_template_iteration_oid AND ls.status_code = i.status "
            + "AND ls.tenant_oid = p.tenant_oid " +
            "<where>" +
            "p.type_definition_code = #{typeCode}" +
            "<if test='clsOids != null and clsOids.size() > 0'>" +
            " AND p.cls_oid IN <foreach item='c' collection='clsOids' open='(' separator=',' close=')'>#{c}</foreach>" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (p.name ILIKE CONCAT('%',#{keyword},'%') OR p.number ILIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</where>" +
            "ORDER BY p.created_at DESC" +
            "</script>")
    List<Map<String, Object>> selectLibraryItems(@Param("clsOids") List<String> clsOids,
                                                 @Param("keyword") String keyword,
                                                 @Param("typeCode") String typeCode);
}
