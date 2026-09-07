/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper;

import cn.ck.plm.resource.entity.ComponentCategory;

import java.util.List;

/**
 * 电子元器件分类数据访问接口，对应表 {@code ck_component_category}。
 */
public interface ComponentCategoryMapper {

    int insert(ComponentCategory category);

    int update(ComponentCategory category);

    int deleteByOid(String oid);

    ComponentCategory selectByOid(String oid);

    /** 查询根分类（parent 为 NULL） */
    List<ComponentCategory> selectRoots();

    /** 查询某父分类下的子分类 */
    List<ComponentCategory> selectByParentOid(String parentCategoryOid);

    /** 全部分类（扁平，前端/服务层组树） */
    List<ComponentCategory> selectAll();

    /** 同一父分类下名称是否重复 */
    int existsByName(String parentCategoryOid, String name, String excludeOid);

    /** 某分类下的子分类数量 */
    int countByParentOid(String parentCategoryOid);
}
