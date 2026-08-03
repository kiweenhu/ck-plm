/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.mapper;

import cn.ck.plm.functional.entity.FunctionalEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FunctionalMapper {

    int insert(FunctionalEntity entity);

    int update(FunctionalEntity entity);

    int deleteByOid(@Param("oid") String oid);

    FunctionalEntity selectByOid(@Param("oid") String oid);

    List<FunctionalEntity> selectByContainerOid(@Param("containerOid") String containerOid);

    List<FunctionalEntity> selectByContainerAndStage(@Param("containerOid") String containerOid,
                                                  @Param("stageOid") String stageOid);

    List<FunctionalEntity> selectByFolderOid(@Param("folderOid") String folderOid);

    List<FunctionalEntity> selectAll();
}
