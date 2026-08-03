/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.Token;
import cn.ck.plm.iam.mapper.TokenMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * {@link TokenMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTokenMapper extends TokenMapper {

    @Override
    @Insert("INSERT INTO ck_token (oid, token, username, expire_at, tenant_oid, tenant_name, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{token}, #{username}, #{expireAt}, #{tenantOid}, #{tenantName}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Token token);

    @Override
    @Delete("DELETE FROM ck_token WHERE token = #{token}")
    int deleteByToken(@Param("token") String token);

    @Override
    @Delete("DELETE FROM ck_token WHERE username = #{username}")
    int deleteByUsername(@Param("username") String username);

    @Override
    @Delete("DELETE FROM ck_token WHERE expire_at < NOW()")
    int deleteExpired();

    @Override
    @Select("SELECT oid, token, username, expire_at, tenant_oid, tenant_name, creator, created_at, updater, updated_at " +
            "FROM ck_token WHERE token = #{token}")
    @Results(id = "tokenResult", value = {
            @Result(property = "oid",        column = "oid"),
            @Result(property = "token",      column = "token"),
            @Result(property = "username",   column = "username"),
            @Result(property = "expireAt",   column = "expire_at"),
            @Result(property = "tenantOid",  column = "tenant_oid"),
            @Result(property = "tenantName", column = "tenant_name"),
            @Result(property = "creator",    column = "creator"),
            @Result(property = "createdAt",  column = "created_at"),
            @Result(property = "updater",    column = "updater"),
            @Result(property = "updatedAt",  column = "updated_at")
    })
    Token selectByToken(@Param("token") String token);
}
