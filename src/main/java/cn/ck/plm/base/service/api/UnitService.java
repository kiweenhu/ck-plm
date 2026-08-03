/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.Unit;

import java.util.List;
import java.util.Map;

/**
 * 计量单位服务接口。
 */
public interface UnitService {

    /** 创建单位 */
    Unit create(Unit unit);

    /** 更新单位 */
    Unit update(String oid, Unit unit);

    /** 删除单位 */
    void delete(String oid);

    /** 按名称查询 */
    Unit getByName(String name);

    /** 按 OID 查询 */
    Unit getByOid(String oid);

    /** 按量纲类型查询 */
    List<Unit> listByQuantityType(String quantityType);

    /** 查询所有单位，按量纲分组返回 */
    Map<String, List<Unit>> listAllGrouped();

    /** 查询所有单位（平铺） */
    List<Unit> listAll();

    /** 两个单位之间的换算因子（from → to） */
    double convertFactor(String fromUnitName, String toUnitName);
}
