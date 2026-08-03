/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 通用 IBA 数据存储映射器 —— 操作 {@code ck_type_iba_data} 表。
 *
 * <p>每个实体实例的 IBA 动态属性值以 (entity_type, entity_oid, attr_code, attr_value) 行存储，
 * 通过 {@link cn.ck.plm.softtype.IBADataService} 统一调用。
 */
public interface IBADataMapper {

    /**
     * 删除指定实体的全部 IBA 数据（save 前的清理步骤）。
     */
    int deleteByEntity(@Param("entityType") String entityType,
                       @Param("entityOid") String entityOid);

    /**
     * 插入一条 IBA 属性值。
     */
    int insert(@Param("entityType") String entityType,
               @Param("entityOid") String entityOid,
               @Param("attrCode") String attrCode,
               @Param("attrValue") String attrValue,
               @Param("tenantOid") String tenantOid,
               @Param("creator") String creator,
               @Param("updater") String updater);

    /**
     * 查询指定实体的全部 IBA 数据，返回 attr_code → attr_value 列表。
     */
    List<Map<String, Object>> selectByEntity(@Param("entityType") String entityType,
                                             @Param("entityOid") String entityOid);
}
