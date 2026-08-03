/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 类型-生命周期模板关联 Mapper 接口。
 */
public interface TypeLifecycleTemplateLinkMapper {

    /**
     * 插入关联记录
     */
    int insert(TypeLifecycleTemplateLink link);

    /**
     * 更新关联记录
     */
    int update(TypeLifecycleTemplateLink link);

    /**
     * 根据 OID 删除
     */
    int deleteByOid(@Param("oid") String oid);

    /**
     * 根据类型 OID 删除（解除该类型的生命周期模板绑定）
     */
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    /**
     * 根据类型 OID 查询关联
     */
    TypeLifecycleTemplateLink selectByTypeOid(@Param("typeOid") String typeOid);

    /**
     * 根据 OID 查询
     */
    TypeLifecycleTemplateLink selectByOid(@Param("oid") String oid);

    /**
     * 查询所有关联
     */
    List<TypeLifecycleTemplateLink> selectAll();

    /**
     * 根据生命周期模板编码查询所有关联的类型
     */
    List<TypeLifecycleTemplateLink> selectByTemplateCode(@Param("lifecycleTemplateCode") String lifecycleTemplateCode);

    /**
     * 检查类型是否已绑定生命周期模板
     */
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
