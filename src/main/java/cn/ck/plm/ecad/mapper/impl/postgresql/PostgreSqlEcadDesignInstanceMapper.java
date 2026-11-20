/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper.impl.postgresql;

import cn.ck.plm.ecad.entity.EcadDesignInstance;
import cn.ck.plm.ecad.mapper.EcadDesignInstanceMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EcadDesignInstanceMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>说明：{@code selectPartOidByNumber} 直查 {@code ck_part} 表——部件按编号的
 * 精确匹配在 part 模块暂无现成方法，为避免为单条 SQL 引入跨模块依赖，先在
 * 电子域内实现；part 模块补上 findByNumber 后应切换过去。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEcadDesignInstanceMapper extends EcadDesignInstanceMapper {

    @Override
    @Insert("INSERT INTO ck_design_instance (oid, design_oid, design_type, reference, " +
            "component_oid, footprint_oid, symbol_oid, quantity, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{designOid}, #{designType}, #{reference}, " +
            "#{componentOid}, #{footprintOid}, #{symbolOid}, #{quantity}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EcadDesignInstance instance);

    @Override
    @Delete("DELETE FROM ck_design_instance WHERE design_oid = #{designOid} AND design_type = #{designType}")
    int deleteByDesign(@Param("designOid") String designOid, @Param("designType") String designType);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, design_oid, design_type, reference, component_oid, " +
            "footprint_oid, symbol_oid, quantity, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_design_instance ";

    @Select(SELECT_COLUMNS + "WHERE design_oid = #{designOid} AND design_type = #{designType} " +
            "ORDER BY reference")
    @Results(id = "ecadDesignInstanceResult", value = {
            @Result(property = "oid",           column = "oid"),
            @Result(property = "designOid",     column = "design_oid"),
            @Result(property = "designType",    column = "design_type"),
            @Result(property = "reference",     column = "reference"),
            @Result(property = "componentOid",  column = "component_oid"),
            @Result(property = "footprintOid",  column = "footprint_oid"),
            @Result(property = "symbolOid",     column = "symbol_oid"),
            @Result(property = "quantity",      column = "quantity"),
            @Result(property = "tenantOid",     column = "tenant_oid"),
            @Result(property = "creator",       column = "creator"),
            @Result(property = "createdAt",     column = "created_at"),
            @Result(property = "updater",       column = "updater"),
            @Result(property = "updatedAt",     column = "updated_at")
    })
    @Override
    List<EcadDesignInstance> selectByDesign(@Param("designOid") String designOid,
                                            @Param("designType") String designType);

    @Override
    @Select("SELECT oid FROM ck_part WHERE number = #{number} AND delete_mark = FALSE LIMIT 1")
    String selectPartOidByNumber(@Param("number") String number);
}
