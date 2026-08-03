/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.TeamMember;
import cn.ck.plm.product.mapper.TeamMemberMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link TeamMemberMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTeamMemberMapper extends TeamMemberMapper {

    @Override
    @Insert("INSERT INTO ck_team_member (oid, team_oid, user_id, role_name, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{teamOid}, #{userId}, #{roleName}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TeamMember member);

    @Override
    @Delete("DELETE FROM ck_team_member WHERE team_oid = #{teamOid} AND user_id = #{userId}")
    int deleteByTeamAndUser(@Param("teamOid") String teamOid, @Param("userId") String userId);

    @Override
    @Delete("DELETE FROM ck_team_member WHERE team_oid = #{teamOid}")
    int deleteByTeamOid(@Param("teamOid") String teamOid);

    @Select("SELECT oid, team_oid, user_id, role_name, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_team_member WHERE team_oid = #{teamOid} ORDER BY user_id ASC")
    @Results(id = "teamMemberResult", value = {
            @Result(property = "oid",       column = "oid"),
            @Result(property = "teamOid",   column = "team_oid"),
            @Result(property = "userId",    column = "user_id"),
            @Result(property = "roleName",  column = "role_name"),
            @Result(property = "creator",   column = "creator"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updater",   column = "updater"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    @Override
    List<TeamMember> selectByTeamOid(@Param("teamOid") String teamOid);

    @Select("SELECT COUNT(*) FROM ck_team_member WHERE team_oid = #{teamOid} AND user_id = #{userId}")
    @Override
    boolean existsByTeamAndUser(@Param("teamOid") String teamOid, @Param("userId") String userId);
}
