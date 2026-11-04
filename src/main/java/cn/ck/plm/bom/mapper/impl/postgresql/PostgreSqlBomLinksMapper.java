/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

import cn.ck.plm.bom.dto.BomMaterialVO;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.mapper.BomLinksMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * BomLinks PostgreSQL 持久化实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlBomLinksMapper extends BomLinksMapper {

    @Override
    @Insert("INSERT INTO ck_bom_links (oid, code, name, description, " +
            "parent_iteration_oid, child_part_oid, child_iteration_oid, resolved_iteration_oid, " +
            "quantity, unit, line_number, unit_cost, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, " +
            "#{parentIterationOid}, #{childPartOid}, #{childIterationOid}, #{resolvedIterationOid}, " +
            "#{quantity}, #{unit}, #{lineNumber}, #{unitCost}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(BomLinks bomLinks);

    @Override
    @Select("SELECT COALESCE(MAX(line_number), 0) FROM ck_bom_links WHERE parent_iteration_oid = #{parentIterationOid}")
    Integer selectMaxLineNumber(@Param("parentIterationOid") String parentIterationOid);

    @Override
    @Update("UPDATE ck_bom_links SET code = #{code}, name = #{name}, description = #{description}, " +
            "parent_iteration_oid = #{parentIterationOid}, child_part_oid = #{childPartOid}, " +
            "child_iteration_oid = #{childIterationOid}, resolved_iteration_oid = #{resolvedIterationOid}, " +
            "quantity = #{quantity}, unit = #{unit}, line_number = #{lineNumber}, unit_cost = #{unitCost}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(BomLinks bomLinks);

    @Override
    @Delete("DELETE FROM ck_bom_links WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM ck_bom_links WHERE parent_iteration_oid = #{parentIterationOid}")
    int deleteByParentIterationOid(@Param("parentIterationOid") String parentIterationOid);

    String SELECT_COLUMNS = "SELECT oid, code, name, description, " +
            "parent_iteration_oid, child_part_oid, child_iteration_oid, resolved_iteration_oid, " +
            "quantity, unit, line_number, unit_cost, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_bom_links ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "bomLinksResult", value = {
            @Result(property = "oid",                  column = "oid"),
            @Result(property = "code",                 column = "code"),
            @Result(property = "name",                 column = "name"),
            @Result(property = "description",          column = "description"),
            @Result(property = "parentIterationOid",   column = "parent_iteration_oid"),
            @Result(property = "childPartOid",         column = "child_part_oid"),
            @Result(property = "childIterationOid",    column = "child_iteration_oid"),
            @Result(property = "resolvedIterationOid", column = "resolved_iteration_oid"),
            @Result(property = "quantity",             column = "quantity"),
            @Result(property = "unit",                 column = "unit"),
            @Result(property = "lineNumber",           column = "line_number"),
            @Result(property = "unitCost",             column = "unit_cost"),
            @Result(property = "tenantOid",            column = "tenant_oid"),
            @Result(property = "creator",              column = "creator"),
            @Result(property = "createdAt",            column = "created_at"),
            @Result(property = "updater",              column = "updater"),
            @Result(property = "updatedAt",            column = "updated_at")
    })
    BomLinks selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_iteration_oid = #{parentIterationOid} ORDER BY line_number ASC")
    @ResultMap("bomLinksResult")
    List<BomLinks> selectByParentIterationOid(@Param("parentIterationOid") String parentIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE child_part_oid = #{childPartOid}")
    @ResultMap("bomLinksResult")
    List<BomLinks> selectByChildPartOid(@Param("childPartOid") String childPartOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE child_iteration_oid = #{childIterationOid}")
    @ResultMap("bomLinksResult")
    List<BomLinks> selectByChildIterationOid(@Param("childIterationOid") String childIterationOid);

    @Override
    @Update("UPDATE ck_bom_links SET resolved_iteration_oid = #{resolvedIterationOid}, " +
            "updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateResolvedIterationOid(@Param("oid") String oid, @Param("resolvedIterationOid") String resolvedIterationOid);

    @Override
    @Select(SELECT_COLUMNS)
    @ResultMap("bomLinksResult")
    List<BomLinks> selectAll();

    /**
     * 容器（产品系列 / 型号）下<b>所有零件</b>的物料清单，一行 = 一个零件。
     *
     * <p>2026-10-22 改口径：以前是从 {@code ck_bom_links} 起手，只列"被 BOM 行用到的子件"——
     * 一个刚建好零件、还没搭 BOM 的产品，物料清单永远是空的（用户实测：X7 智能手表 0 种物料）。
     * 现在以 {@code ck_part} 起手：<b>只要 container_oid 属于本系列/型号</b>就进清单，
     * 不区分阶段、不区分文件夹；BOM 用量只是可选补充（LEFT JOIN，没有 BOM 行就是 0）。
     *
     * <p>{@code i.latest = TRUE}：每个零件取<b>最新大版本的最新小版本</b> ——
     * revise 时旧迭代 latest 置 false、新迭代 latest = true，checkin 也只在最新迭代上累积，
     * 所以这一个条件等价于用户说的"最新大版本的最新小版本"。
     *
     * <p>容器口径：{@code p.container_oid = 当前容器}，<b>不回溯父系列</b>。
     * 型号页只看挂在型号上的零件、系列页只看挂在系列上的零件 —— 父子容器各看各的，
     * 否则型号页会混进父系列的零件（2026-10-23 用户反馈"不属于当前 containerOid 的内容又出现了"）。
     *
     * <p>型号页清单曾经为空的真正原因是前端懒加载没触发（默认页签就是 bom，watch 等不到切换），
     * 不是容器口径 —— 别再用"并父系列"补数据。
     *
     * <p><b>为什么 BOM 过滤写在 JOIN 条件里而不是 WHERE</b>：写 WHERE 会让"没用过任何 BOM"的
     * 零件被过滤掉，正是这次要修的 bug；写在 LEFT JOIN 上则只影响聚合结果。
     *
     * <p><b>为什么不能有子查询形式的 GROUP BY</b>：租户拦截器把 {@code p.tenant_oid} 插在
     * 第一个收尾子句（GROUP BY）之前，子查询里若有 GROUP BY 会被插错位置（见
     * {@code TenantStatementInterceptor#clauseStart}）。这里只有外层一个 GROUP BY。
     */
    @Override
    @Select("SELECT p.oid AS \"childPartOid\", "
            + "p.oid AS \"oid\", 'PART' AS \"entityType\", "
            + "p.number AS \"number\", p.number AS \"code\", p.name AS \"name\", "
            + "p.type_definition_code AS \"typeCode\", "
            + "td.name AS \"typeDefinitionName\", "
            + "i.display_version AS \"version\", i.display_version AS \"displayVersion\", "
            + "i.status AS \"status\", i.status AS \"statusCode\", "
            + "i.oid AS \"iterationOid\", "
            + "i.lifecycle_template_iteration_oid AS \"lifecycleTemplateIterationOid\", "
            + "i.checked_out AS \"checkedOut\", i.checked_out_by AS \"checkedOutBy\", "
            + "i.checked_out_comment AS \"checkedOutComment\", "
            + "i.unit AS \"unit\", "
            + "COALESCE(SUM(bl.quantity), 0) AS \"totalQuantity\", "
            + "COUNT(bl.oid) AS \"lineCount\", "
            + "STRING_AGG(DISTINCT parent.number, ', ') AS \"parentNumbers\" "
            + "FROM ck_part p "
            + "JOIN ck_part_iteration i ON i.master_oid = p.oid AND i.latest = TRUE "
            // 类型显示名：类型定义按租户各一份，取同租户那份（缺省时前端回退显示 code）
            + "LEFT JOIN ck_type_definition td ON td.code = p.type_definition_code AND td.tenant_oid = p.tenant_oid "
            + "LEFT JOIN ck_bom_links bl ON bl.child_part_oid = p.oid "
            + "     AND bl.parent_iteration_oid IN ("
            + "         SELECT pi.oid FROM ck_part_iteration pi "
            + "         JOIN ck_part bp ON bp.oid = pi.master_oid "
            + "         WHERE pi.latest = TRUE AND bp.container_oid = #{containerOid}) "
            + "LEFT JOIN ck_part_iteration pi ON pi.oid = bl.parent_iteration_oid AND pi.latest = TRUE "
            + "LEFT JOIN ck_part parent ON parent.oid = pi.master_oid "
            + "WHERE p.container_oid = #{containerOid} "
            + "GROUP BY p.oid, p.number, p.name, p.type_definition_code, td.name, "
            + "i.oid, i.display_version, i.status, i.lifecycle_template_iteration_oid, "
            + "i.checked_out, i.checked_out_by, i.checked_out_comment, i.unit "
            + "ORDER BY p.number NULLS LAST")
    List<BomMaterialVO> selectMaterialsByContainerOid(@Param("containerOid") String containerOid);
}
