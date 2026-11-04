/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.Unit;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 计量单位 Mapper 接口。
 *
 * <p>单位**按租户隔离**（2026-10-10 起）：除 {@code insert} 带 {@code tenantOid} 外，
 * 各查询方法也**一律显式带 tenantOid** —— 与 {@code ClassificationMapper} 同款口径。
 * 为什么不只靠 {@code TenantStatementInterceptor} 兜：拦截器在 {@code TenantContext}
 * 为空时不注入租户条件，那时业务表会看到所有租户的数据；显式写就与上下文无关了。
 */
public interface UnitMapper {

    int insert(Unit unit);

    int update(Unit unit);

    int deleteByOid(@Param("oid") String oid);

    /** 按名称精确查询（限本租户） */
    Unit selectByName(@Param("name") String name, @Param("tenantOid") String tenantOid);

    /** 按 OID 查询 */
    Unit selectByOid(@Param("oid") String oid);

    /** 按量纲类型查询本单位所有单位 */
    List<Unit> selectByQuantityType(@Param("quantityType") String quantityType,
                                    @Param("tenantOid") String tenantOid);

    /** 查询本租户所有单位（按量纲 + 排序） */
    List<Unit> selectAll(@Param("tenantOid") String tenantOid);

    /** 检查名称在本租户内是否已存在 */
    int existsByName(@Param("name") String name, @Param("tenantOid") String tenantOid);

    /** 按基准单位名查询（限本租户） */
    List<Unit> selectByBaseUnitName(@Param("baseUnitName") String baseUnitName,
                                    @Param("tenantOid") String tenantOid);
}
