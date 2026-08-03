/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cls.mapper;

import cn.ck.plm.cls.entity.ClsPageLayout;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类 IBA 页面布局 Mapper 接口。
 */
public interface ClsPageLayoutMapper {

    /** 按分类 OID + 租户查询该分类下所有已保存的布局 */
    List<ClsPageLayout> selectAllByClsOid(@Param("clsOid") String clsOid,
                                           @Param("tenantOid") String tenantOid);

    /** 根据 clsOid + operationCode + 租户查询 */
    ClsPageLayout selectByClsAndOperation(@Param("clsOid") String clsOid,
                                           @Param("operationCode") String operationCode,
                                           @Param("tenantOid") String tenantOid);

    /** 插入一条布局记录 */
    int insert(ClsPageLayout layout);

    /** 更新布局记录（全字段） */
    int update(ClsPageLayout layout);

    /** 更新布局 JSON 和操作名称 */
    int updateLayout(@Param("oid") String oid,
                     @Param("layoutJson") String layoutJson,
                     @Param("operationName") String operationName);

    /** 删除布局记录 */
    int deleteByClsAndOperation(@Param("clsOid") String clsOid,
                                 @Param("operationCode") String operationCode,
                                 @Param("tenantOid") String tenantOid);

    /** 删除某分类下的所有布局 */
    int deleteAllByClsOid(@Param("clsOid") String clsOid);
}
