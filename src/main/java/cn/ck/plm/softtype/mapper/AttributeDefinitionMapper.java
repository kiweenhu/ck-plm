/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.AttributeDefinition;

import java.util.List;

/**
 * 实体属性定义数据访问接口。
 */
public interface AttributeDefinitionMapper {

    int insert(AttributeDefinition def);

    int update(AttributeDefinition def);

    int deleteByOid(String oid);

    AttributeDefinition selectByOid(String oid);

    /**
     * 查询某实体的所有属性定义（按 sort_order 排序）。
     *
     * <p>租户过滤由 {@code TenantStatementInterceptor} 负责（本表属"平台共享 + 租户可覆写"口径：
     * 条件为 {@code tenant_oid IN (平台租户, 当前租户)}），调用方拿到的是"平台一份 + 本租户一份"，
     * 同名字段由 Service 层去重（租户覆写优先）。
     */
    List<AttributeDefinition> selectByEntityName(String entityName);

    /** 检查唯一键 (entity_name, field_name, tenant_oid) 是否存在 */
    int existsByEntityAndField(String entityName, String fieldName, String tenantOid);

    /** 根据 ibaOid 删除关联记录 */
    int deleteByIbaOid(String ibaOid);

    /**
     * 删除某实体在指定租户下的所有 SYSTEM 来源属性（重建前清理）。
     *
     * <p>必须带租户：平台元数据播种只该清"平台那一份"，不能把租户装好的行业属性一起删掉。
     */
    int deleteSystemByEntityAndTenant(String entityName, String tenantOid);
}
