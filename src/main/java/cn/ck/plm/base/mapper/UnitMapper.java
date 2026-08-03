/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.Unit;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 计量单位 Mapper 接口。
 */
public interface UnitMapper {

    int insert(Unit unit);

    int update(Unit unit);

    int deleteByOid(@Param("oid") String oid);

    /** 按名称精确查询 */
    Unit selectByName(@Param("name") String name);

    /** 按 OID 查询 */
    Unit selectByOid(@Param("oid") String oid);

    /** 按量纲类型查询所有单位 */
    List<Unit> selectByQuantityType(@Param("quantityType") String quantityType);

    /** 查询所有单位（按量纲 + 排序） */
    List<Unit> selectAll();

    /** 检查名称是否已存在 */
    int existsByName(@Param("name") String name);

    /** 按基准单位名查询 */
    List<Unit> selectByBaseUnitName(@Param("baseUnitName") String baseUnitName);
}
