/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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

    /** 查询某实体的所有属性定义（按 sort_order 排序） */
    List<AttributeDefinition> selectByEntityName(String entityName);

    /** 检查唯一键 (entity_name, field_name) 是否存在 */
    int existsByEntityAndField(String entityName, String fieldName);

    /** 根据 ibaOid 删除关联记录 */
    int deleteByIbaOid(String ibaOid);

    /** 删除某实体的所有 SYSTEM 来源属性（用于重建前清理） */
    int deleteByEntityName(String entityName);
}
