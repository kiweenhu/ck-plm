/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

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
            "quantity, unit, line_number, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, " +
            "#{parentIterationOid}, #{childPartOid}, #{childIterationOid}, #{resolvedIterationOid}, " +
            "#{quantity}, #{unit}, #{lineNumber}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(BomLinks bomLinks);

    @Override
    @Update("UPDATE ck_bom_links SET code = #{code}, name = #{name}, description = #{description}, " +
            "parent_iteration_oid = #{parentIterationOid}, child_part_oid = #{childPartOid}, " +
            "child_iteration_oid = #{childIterationOid}, resolved_iteration_oid = #{resolvedIterationOid}, " +
            "quantity = #{quantity}, unit = #{unit}, line_number = #{lineNumber}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(BomLinks bomLinks);

    @Override
    @Delete("DELETE FROM ck_bom_links WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, code, name, description, " +
            "parent_iteration_oid, child_part_oid, child_iteration_oid, resolved_iteration_oid, " +
            "quantity, unit, line_number, " +
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
}
