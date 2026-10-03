/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

    /**
     * 删除该用户除指定 token 外的全部 token —— 支撑「个人设置 → 退出其他设备」。
     *
     * @param username 用户名
     * @param token    当前会话 token（保留，不动）
     * @return 被删除的条数
     */
    int deleteOtherTokens(String username, String token);

    int deleteExpired();

    Token selectByToken(String token);
}
