/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.Tenant;
import java.util.List;

/**
 * 租户数据访问接口。
 */
public interface TenantMapper {

    int insert(Tenant tenant);

    int update(Tenant tenant);

    Tenant selectByOid(String oid);

    Tenant selectByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    List<Tenant> selectPending();

    int countPending();

    /** 查询所有已激活的租户 */
    List<Tenant> selectActive();
}
