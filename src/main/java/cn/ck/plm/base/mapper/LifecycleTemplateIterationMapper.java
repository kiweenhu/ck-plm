/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.LifecycleTemplateIteration;

import java.util.List;

/**
 * LifecycleTemplateIteration 子版本数据访问接口。
 */
public interface LifecycleTemplateIterationMapper {

    int insert(LifecycleTemplateIteration iteration);

    int update(LifecycleTemplateIteration iteration);

    int deleteByOid(String oid);

    LifecycleTemplateIteration selectByOid(String oid);

    /** 查询某模板的最新子版本 */
    LifecycleTemplateIteration selectLatestByMasterOid(String masterOid);

    /** 查询某模板的所有子版本（按 revision, iteration 降序） */
    List<LifecycleTemplateIteration> selectByMasterOid(String masterOid);
}
