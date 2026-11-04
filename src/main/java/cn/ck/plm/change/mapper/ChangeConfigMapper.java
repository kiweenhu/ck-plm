/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeConfig;

/**
 * 变更租户配置 Mapper。
 *
 * <p>主键是 {@code tenant_oid}（不是 oid），每租户至多一行；服务层读不到时按默认值兜底并落库。
 */
public interface ChangeConfigMapper {

    int insert(ChangeConfig config);

    int update(ChangeConfig config);

    ChangeConfig selectByTenant(String tenantOid);
}
