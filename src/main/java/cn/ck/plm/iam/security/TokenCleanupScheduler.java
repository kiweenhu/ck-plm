/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

import cn.ck.plm.iam.mapper.TokenMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理过期 Token —— 每 1 小时执行一次。
 *
 * <p>过期 logic 已在 {@link TokenStore#validate} 中做惰性删除，
 * 此定时任务作为兜底，清理未被 validate 触发的脏数据。
 */
@Component
public class TokenCleanupScheduler {

    private final TokenMapper tokenMapper;

    public TokenCleanupScheduler(TokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    /**
     * 每小时执行一次，删除所有已过期的 token 记录。
     */
    @Scheduled(fixedRate = 3600_000)
    public void cleanExpiredTokens() {
        tokenMapper.deleteExpired();
    }
}
