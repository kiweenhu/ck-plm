/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.util.TenantContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

/**
 * MyBatis 多租户拦截器 —— INSERT 时自动填充 tenantId，
 * 配合 {@link cn.ck.plm.base.config.TenantStatementInterceptor} 完成 SQL 改写。
 *
 * <p>本拦截器工作在 Executor 层，负责在 INSERT 前自动将
 * {@link TenantContext} 中的租户标识填充到 {@link TenantEntity} 实体的
 * {@code tenantId} 字段中。
 *
 * <p>SELECT / UPDATE / DELETE 的租户过滤由
 * {@link TenantStatementInterceptor}（StatementHandler 层）完成。
 *
 * <p><b>单租户兼容：</b>当 {@link TenantContext} 未显式设置时，使用默认值 {@code "default"}。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class TenantFillInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        if (SqlCommandType.INSERT != ms.getSqlCommandType()) {
            return invocation.proceed();
        }

        String tenantOid = TenantContext.get();
        fillTenantOid(parameter, tenantOid);

        return invocation.proceed();
    }

    private void fillTenantOid(Object parameter, String tenantOid) {
        if (parameter instanceof TenantEntity) {
            TenantEntity entity = (TenantEntity) parameter;
            if (entity.getTenantOid() == null) {
                entity.setTenantOid(tenantOid);
            }
        } else if (parameter instanceof Collection) {
            for (Object item : (Collection<?>) parameter) {
                if (item instanceof TenantEntity) {
                    TenantEntity entity = (TenantEntity) item;
                    if (entity.getTenantOid() == null) {
                        entity.setTenantOid(tenantOid);
                    }
                }
            }
        } else if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            for (Object value : paramMap.values()) {
                if (value instanceof Collection) {
                    for (Object item : (Collection<?>) value) {
                        if (item instanceof TenantEntity) {
                            TenantEntity entity = (TenantEntity) item;
                            if (entity.getTenantOid() == null) {
                                entity.setTenantOid(tenantOid);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
