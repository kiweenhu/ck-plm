/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

import cn.ck.plm.iam.entity.Token;
import cn.ck.plm.iam.mapper.TokenMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token 存储 —— 数据库持久化的认证令牌管理。
 *
 * <p>每次登录在 ck_token 表中生成一条 token 记录，有效期 3 天。
 * 校验时从数据库查询并检查是否过期，过期自动删除。
 *
 * <p>相比旧版内存 ConcurrentHashMap 实现：
 * <ul>
 *   <li>服务重启后 token 不丢失</li>
 *   <li>支持多实例部署（共享数据库）</li>
 *   <li>定时任务自动清理过期 token</li>
 * </ul>
 */
@Component
public class TokenStore {

    /** 默认有效期：3 天 */
    private static final int DEFAULT_TTL_DAYS = 3;

    private final TokenMapper tokenMapper;

    public TokenStore(TokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    /**
     * 为用户创建新 token，关联租户信息。
     *
     * @param username   用户名
     * @param tenantOid  租户 oid（ck_tenant.oid）
     * @param tenantName 租户名称
     * @return token 值
     */
    public String create(String username, String tenantOid, String tenantName) {
        String tokenValue = UUID.randomUUID().toString();
        LocalDateTime expireAt = LocalDateTime.now().plusDays(DEFAULT_TTL_DAYS);
        Token token = new Token(tokenValue, username, expireAt);
        token.setTenantOid(tenantOid);
        token.setTenantName(tenantName);
        tokenMapper.insert(token);
        return tokenValue;
    }

    /**
     * 校验 token 是否有效，有效返回 TokenInfo（含 username + tenantId + tenantName），否则返回 null。
     */
    public TokenInfo validate(String tokenValue) {
        if (tokenValue == null) {
            return null;
        }
        Token token = tokenMapper.selectByToken(tokenValue);
        if (token == null) {
            return null;
        }
        if (token.isExpired()) {
            tokenMapper.deleteByToken(tokenValue);
            return null;
        }
        return TokenInfo.of(token.getUsername(), token.getTenantOid(), token.getTenantName());
    }

    /**
     * 移除 token（登出）。
     */
    public void remove(String tokenValue) {
        if (tokenValue != null) {
            tokenMapper.deleteByToken(tokenValue);
        }
    }

    /**
     * 移除用户的所有 token。
     */
    public void removeByUsername(String username) {
        tokenMapper.deleteByUsername(username);
    }
}
