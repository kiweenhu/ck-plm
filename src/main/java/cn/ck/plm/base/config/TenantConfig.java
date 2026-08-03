/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 多租户配置 —— 将 TenantFillInterceptor / TenantStatementInterceptor
 * 注册到所有 SqlSessionFactory。
 */
@Configuration
public class TenantConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantConfig.class);

    @Autowired(required = false)
    private List<SqlSessionFactory> sqlSessionFactories;

    @Autowired
    private TenantFillInterceptor tenantFillInterceptor;

    @Autowired
    private TenantStatementInterceptor tenantStatementInterceptor;

    @PostConstruct
    public void registerTenantInterceptors() {
        if (sqlSessionFactories == null) {
            log.warn("未找到 SqlSessionFactory，跳过注册多租户拦截器");
            return;
        }
        for (SqlSessionFactory factory : sqlSessionFactories) {
            factory.getConfiguration().addInterceptor(tenantFillInterceptor);
            factory.getConfiguration().addInterceptor(tenantStatementInterceptor);
            log.info("多租户拦截器已注册到 SqlSessionFactory");
        }
    }
}
