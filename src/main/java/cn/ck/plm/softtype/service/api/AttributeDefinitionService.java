/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.entity.IBA;

import java.util.List;

/**
 * 实体属性定义服务接口。
 *
 * <p>管理实体属性的元数据注册（系统字段 + IBA 扩展），
 * 为 CRUD UI 配置化布局提供数据支撑。
 */
public interface AttributeDefinitionService {

    /** 获取某实体的所有属性定义（含系统 + IBA），仅从 ck_attribute_definition 表查询 */
    List<AttributeDefinition> findByEntityName(String entityName);

    /**
     * 获取实体的完整属性定义列表（系统 + IBA 动态合并）。
     *
     * <p>先从 ck_attribute_definition 取已注册属性，
     * 再从 ck_type_iba 取该实体的 IBA 关联，自动注册缺失的 IBA 属性并合并返回。
     */
    List<AttributeDefinition> findByEntity(String entityCode, String entityOid, String entityType);

    /** 注册单个属性定义（幂等：已存在则跳过） */
    AttributeDefinition register(AttributeDefinition def);

    /** 批量注册系统属性（幂等） */
    int registerSystemAttributes(String entityName, List<AttributeDefinition> defs);

    /** 重建实体的系统属性：先清空旧 SYSTEM 属性，再注册新定义 */
    int reinitSystemAttributes(String entityName, List<AttributeDefinition> defs);

    /** 从 IBA 生成属性定义并注册到指定实体 */
    AttributeDefinition registerFromIba(IBA iba, String entityName);

    /** 移除某个 IBA 对应的属性定义 */
    void removeByIbaOid(String ibaOid);

    /** 更新单个属性定义的布局配置 */
    AttributeDefinition update(AttributeDefinition def);

    /** 批量更新布局配置（displayName、uiComponent、searchable、listable、editable、sortOrder、enabled） */
    int batchUpdateLayout(List<AttributeDefinition> defs);
}
