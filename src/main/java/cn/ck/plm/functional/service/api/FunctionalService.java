/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.service.api;

import cn.ck.plm.functional.dto.FunctionalVO;
import cn.ck.plm.functional.entity.FunctionalEntity;

import java.util.List;

public interface FunctionalService {

    FunctionalEntity create(FunctionalEntity entity, String ckfileOid, String attachmentOid);

    FunctionalEntity update(FunctionalEntity entity);

    void delete(String oid);

    FunctionalEntity findByOid(String oid);

    List<FunctionalEntity> findByContainerOid(String containerOid);

    List<FunctionalEntity> findByContainerAndStage(String containerOid, String stageOid);

    List<FunctionalEntity> findByFolderOid(String folderOid);

    List<FunctionalVO> findVOsByFolder(String folderOid);
}
