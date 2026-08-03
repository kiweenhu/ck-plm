/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.PageLayout;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 页面布局 Mapper 接口。
 */
public interface PageLayoutMapper {

    /** 按实体 OID + entityCode + 租户（当前租户+平台租户）查询该实体下所有已保存的布局（每个 operation_code 去重取租户级优先） */
    List<PageLayout> selectAllByEntity(@Param("entityOid") String entityOid,
                                       @Param("entityCode") String entityCode,
                                       @Param("tenantOid") String tenantOid,
                                       @Param("platformOid") String platformOid);

    /** 根据 entityOid + operationCode + 当前租户优先查询 */
    PageLayout selectByEntityAndOperation(@Param("entityOid") String entityOid,
                                          @Param("operationCode") String operationCode,
                                          @Param("tenantOid") String tenantOid,
                                          @Param("platformOid") String platformOid);

    /** 根据 entityCode + operationCode + 当前租户优先查询 */
    PageLayout selectByEntityCodeAndOperation(@Param("entityCode") String entityCode,
                                              @Param("operationCode") String operationCode,
                                              @Param("tenantOid") String tenantOid,
                                              @Param("platformOid") String platformOid);

    /** 精确按 entityOid + entityCode + operationCode + 指定租户查询（不回落平台） */
    PageLayout selectByTenant(@Param("entityOid") String entityOid,
                              @Param("entityCode") String entityCode,
                              @Param("operationCode") String operationCode,
                              @Param("tenantOid") String tenantOid);

    /** 插入一条布局记录 */
    int insert(PageLayout layout);

    /** 更新布局记录（全字段） */
    int update(PageLayout layout);

    /** 更新布局 JSON 和操作名称 */
    int updateLayout(@Param("oid") String oid, @Param("layoutJson") String layoutJson,
                     @Param("operationName") String operationName);

    /** 删除布局记录 */
    int deleteByEntityAndOperation(@Param("entityOid") String entityOid,
                                    @Param("operationCode") String operationCode,
                                    @Param("tenantOid") String tenantOid);

    /** 删除某实体下的所有布局（级联删除） */
    int deleteAllByEntity(@Param("entityOid") String entityOid);
}
