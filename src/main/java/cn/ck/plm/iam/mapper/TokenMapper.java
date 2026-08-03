/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.Token;

/**
 * Token 数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>由 {@code mapper.impl.postgresql.PostgreSqlTokenMapper} 对接 PostgreSQL。
 */
public interface TokenMapper {

    int insert(Token token);

    int deleteByToken(String token);

    int deleteByUsername(String username);

    int deleteExpired();

    Token selectByToken(String token);
}
