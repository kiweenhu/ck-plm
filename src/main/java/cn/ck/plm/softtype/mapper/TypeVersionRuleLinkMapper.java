/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeVersionRuleLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 类型-版本规则关联 Mapper 接口。
 */
public interface TypeVersionRuleLinkMapper {

    /**
     * 插入关联记录
     */
    int insert(TypeVersionRuleLink link);

    /**
     * 更新关联记录
     */
    int update(TypeVersionRuleLink link);

    /**
     * 根据 OID 删除
     */
    int deleteByOid(@Param("oid") String oid);

    /**
     * 根据类型 OID 删除（解除该类型的所有版本规则绑定）
     */
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    /**
     * 根据类型 OID 查询关联
     */
    TypeVersionRuleLink selectByTypeOid(@Param("typeOid") String typeOid);

    /**
     * 根据 OID 查询
     */
    TypeVersionRuleLink selectByOid(@Param("oid") String oid);

    /**
     * 查询所有关联
     */
    List<TypeVersionRuleLink> selectAll();

    /**
     * 根据版本规则编码查询所有关联的类型
     */
    List<TypeVersionRuleLink> selectByVersionRuleCode(@Param("versionRuleCode") String versionRuleCode);

    /**
     * 检查类型是否已绑定版本规则
     */
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
