/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * MyBatis 审计拦截器 —— INSERT 时自动填充 createdAt/creator，
 * UPDATE 时自动填充 updatedAt/updater。
 *
 * <p>仅处理参数为 {@link BaseEntity} 及其子类的 Statement，
 * 其他 MappedStatement（如 DELETE、SELECT 或参数非实体）直接放行。
 *
 * <p>用户标识通过 {@link UserContext#get()} 获取。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        SqlCommandType commandType = ms.getSqlCommandType();
        String user = UserContext.get();
        LocalDateTime now = LocalDateTime.now();

        if (parameter instanceof BaseEntity) {
            fillAuditFields((BaseEntity) parameter, commandType, user, now);
        } else if (parameter instanceof Collection) {
            for (Object item : (Collection<?>) parameter) {
                if (item instanceof BaseEntity) {
                    fillAuditFields((BaseEntity) item, commandType, user, now);
                }
            }
        } else if (parameter instanceof Map) {
            // MyBatis 将单个 List/Collection 参数包装为 ParamMap（keys: "list", "collection"）
            // 此处解包并填充集合中每个 BaseEntity 的审计字段
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            for (Object value : paramMap.values()) {
                if (value instanceof Collection) {
                    for (Object item : (Collection<?>) value) {
                        if (item instanceof BaseEntity) {
                            fillAuditFields((BaseEntity) item, commandType, user, now);
                        }
                    }
                }
            }
        }

        return invocation.proceed();
    }

    private void fillAuditFields(BaseEntity entity, SqlCommandType commandType, String user, LocalDateTime now) {
        if (SqlCommandType.INSERT == commandType) {
            if (entity.getOid() == null) {
                entity.setOid(UUID.randomUUID().toString());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(now);
            }
            if (entity.getCreator() == null) {
                entity.setCreator(user);
            }
            if (entity.getUpdatedAt() == null) {
                entity.setUpdatedAt(now);
            }
            if (entity.getUpdater() == null) {
                entity.setUpdater(user);
            }
            // 多租户：自动填充 tenantOid
            if (entity instanceof TenantEntity) {
                TenantEntity tenantEntity = (TenantEntity) entity;
                if (tenantEntity.getTenantOid() == null) {
                    tenantEntity.setTenantOid(TenantContext.get());
                }
            }
        } else if (SqlCommandType.UPDATE == commandType) {
            entity.setUpdatedAt(now);
            entity.setUpdater(user);
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
