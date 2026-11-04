/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.TenantCapability;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租户能力 Mapper（表 {@code ck_tenant_capability}）。
 *
 * <p>幂等键 = 租户 + 能力码（表上有唯一约束）；已存在即跳过，不覆盖 ——
 * 与其它安装器同一口径：管理员后续手动关掉的能力，不该被下一次装包重新打开。
 */
public interface TenantCapabilityMapper {

    int insert(TenantCapability capability);

    /** 该租户是否已有此能力（幂等判定） */
    int exists(@Param("tenantOid") String tenantOid, @Param("capabilityCode") String capabilityCode);

    /** 某租户已启用的能力（供读侧与排障用） */
    List<TenantCapability> selectByTenant(@Param("tenantOid") String tenantOid);
}
