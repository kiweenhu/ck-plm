/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.mapper;

import cn.ck.plm.functional.entity.FunctionalIteration;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FunctionalIterationMapper {

    int insert(FunctionalIteration iteration);

    int update(FunctionalIteration iteration);

    int deleteByOid(@Param("oid") String oid);

    FunctionalIteration selectByOid(@Param("oid") String oid);

    FunctionalIteration selectLatestByMasterOid(@Param("masterOid") String masterOid);

    List<FunctionalIteration> selectByMasterOid(@Param("masterOid") String masterOid);

    List<FunctionalIteration> selectAll();
}
