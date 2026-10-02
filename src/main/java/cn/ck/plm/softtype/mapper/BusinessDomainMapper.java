/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.BusinessDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * 业务域数据访问（表 {@code ck_business_domain}）。
 *
 * <p>单文件注解式：域是平台预置的少量只读数据（6 条），不值得拆接口 + PostgreSQL 实现两层
 * （同 {@code StdPartInboundConfigMapper} 的做法）。域不允许租户自定义，故这里不提供写入方法。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface BusinessDomainMapper {

    String TABLE = "ck_business_domain";

    /**
     * 全部启用的域（类型树第一层，按 sort_order）。
     *
     * <p><b>必须显式带上"当前租户 + 平台租户"</b>：域是平台预置数据（{@code tenant_oid} 固定平台租户），
     * 而全局的租户拦截器会把查询限定到当前租户 → 不加这段条件就一行都读不到（类型树第一层会凭空消失）。
     * 写法与 {@code TypeDefinitionMapper.selectAll(tenantOid, platformOid)} 保持一致。
     */
    @Select("SELECT oid, code, name, icon, description, sort_order, enabled, source, tenant_oid, "
            + "creator, created_at, updater, updated_at FROM " + TABLE + " ORDER BY sort_order, code")
    List<BusinessDomain> selectEnabled(@Param("tenantOid") String tenantOid,
                                       @Param("platformOid") String platformOid);

    /**
     * 按 code 取域（类型归属域时用它解析 domain_oid）。
     *
     * <p>共享表、无租户条件 —— 域是平台预置、全租户同一套。
     */
    @Select("SELECT oid, code, name, icon, description, sort_order, enabled, source, tenant_oid, "
            + "creator, created_at, updater, updated_at FROM " + TABLE + " WHERE code = #{code} LIMIT 1")
    BusinessDomain selectByCode(@Param("code") String code);
}
