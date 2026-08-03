/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.PartIteration;

import java.util.List;

/**
 * PartIteration 子版本数据访问接口，定义数据库无关的持久化契约。
 */
public interface PartIterationMapper {

    int insert(PartIteration iteration);

    int update(PartIteration iteration);

    int deleteByOid(String oid);

    PartIteration selectByOid(String oid);

    /** 查询某主对象的最新子版本 */
    PartIteration selectLatestByMasterOid(String masterOid);

    /** 查询某主对象的所有子版本（按 revision, iteration 降序） */
    List<PartIteration> selectByMasterOid(String masterOid);

    /** 查询指定用户所有检出态的迭代 */
    List<PartIteration> selectCheckedOutByUser(String checkedOutBy);
}
