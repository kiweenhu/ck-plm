/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeClassificationLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 类型-分类关联 Mapper 接口。
 */
public interface TypeClassificationLinkMapper {

    /** 插入关联记录 */
    int insert(TypeClassificationLink link);

    /** 更新关联记录 */
    int update(TypeClassificationLink link);

    /** 根据 OID 删除 */
    int deleteByOid(@Param("oid") String oid);

    /** 根据类型 OID 删除（解除该类型的分类绑定） */
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    /** 根据类型 OID 查询关联 */
    TypeClassificationLink selectByTypeOid(@Param("typeOid") String typeOid);

    /** 根据 OID 查询 */
    TypeClassificationLink selectByOid(@Param("oid") String oid);

    /** 查询所有关联 */
    List<TypeClassificationLink> selectAll();

    /** 根据分类 OID 查询所有绑定了该分类的类型 */
    List<TypeClassificationLink> selectByClassificationOid(@Param("classificationOid") String classificationOid);

    /** 检查类型是否已绑定分类 */
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
