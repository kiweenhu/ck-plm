/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Team;
import cn.ck.plm.product.mapper.TeamMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link TeamMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTeamMapper extends TeamMapper {

    @Override
    @Insert("INSERT INTO ck_team (oid, code, name, description, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Team team);

    @Override
    @Update("UPDATE ck_team SET name = #{name}, description = #{description}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(Team team);

    @Override
    @Delete("DELETE FROM ck_team WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Select("SELECT oid, code, name, description, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_team WHERE oid = #{oid}")
    @Results(id = "teamResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    Team selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, code, name, description, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_team WHERE code = #{code}")
    @ResultMap("teamResult")
    @Override
    Team selectByCode(@Param("code") String code);

    @Select("SELECT oid, code, name, description, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_team ORDER BY code ASC")
    @ResultMap("teamResult")
    @Override
    List<Team> selectAll();

    @Select("SELECT COUNT(*) FROM ck_team WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);
}
