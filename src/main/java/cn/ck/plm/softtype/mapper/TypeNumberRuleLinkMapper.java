/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeNumberRuleLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 类型-编码规则关联 Mapper 接口。
 */
public interface TypeNumberRuleLinkMapper {

    /**
     * 插入关联记录
     */
    int insert(TypeNumberRuleLink link);

    /**
     * 更新关联记录
     */
    int update(TypeNumberRuleLink link);

    /**
     * 根据 OID 删除
     */
    int deleteByOid(@Param("oid") String oid);

    /**
     * 根据类型 OID 删除（解除该类型的所有编码规则绑定）
     */
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    /**
     * 根据类型 OID 查询关联
     */
    TypeNumberRuleLink selectByTypeOid(@Param("typeOid") String typeOid);

    /**
     * 根据 OID 查询
     */
    TypeNumberRuleLink selectByOid(@Param("oid") String oid);

    /**
     * 查询所有关联
     */
    List<TypeNumberRuleLink> selectAll();

    /**
     * 根据编码规则编码查询所有关联的类型
     */
    List<TypeNumberRuleLink> selectByNumberRuleCode(@Param("numberRuleCode") String numberRuleCode);

    /**
     * 检查类型是否已绑定编码规则
     */
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
