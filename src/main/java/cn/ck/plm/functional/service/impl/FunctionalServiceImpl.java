/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.service.impl;

import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.functional.dto.FunctionalVO;
import cn.ck.plm.functional.entity.FunctionalEntity;
import cn.ck.plm.functional.entity.FunctionalIteration;
import cn.ck.plm.functional.mapper.FunctionalIterationMapper;
import cn.ck.plm.functional.mapper.FunctionalMapper;
import cn.ck.plm.functional.service.api.FunctionalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FunctionalServiceImpl implements FunctionalService {

    private final FunctionalMapper FunctionalMapper;
    private final FunctionalIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final NumberService numberService;

    public FunctionalServiceImpl(FunctionalMapper FunctionalMapper,
                             FunctionalIterationMapper iterationMapper,
                             TypeDefinitionMapper typeDefinitionMapper,
                             NumberService numberService) {
        this.FunctionalMapper = FunctionalMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
    }

    @Override
    @Transactional
    public FunctionalEntity create(FunctionalEntity entity, String ckfileOid, String attachmentOid) {
        entity.setOid(UUID.randomUUID().toString());

        // 编码生成（通过 TypeNumberRuleLink 绑定规则后自动生成）
        if (entity.getTypeDefinitionCode() != null) {
            try {
                String number = numberService.generate(entity.getTypeDefinitionCode());
                if (number != null && !number.isEmpty()) entity.setNumber(number);
            } catch (Exception e) {
                // 编码生成失败不阻塞创建流程
            }
        }

        FunctionalMapper.insert(entity);

        // 创建初始迭代
        FunctionalIteration iter = new FunctionalIteration();
        iter.setOid(UUID.randomUUID().toString());
        iter.setMasterOid(entity.getOid());
        iter.setRevision("A");
        iter.setIteration(1);
        iter.setDisplayVersion("A.1");
        iter.setLatest(true);
        iterationMapper.insert(iter);

        return entity;
    }

    @Override
    @Transactional
    public FunctionalEntity update(FunctionalEntity entity) {
        FunctionalMapper.update(entity);
        return FunctionalMapper.selectByOid(entity.getOid());
    }

    @Override
    @Transactional
    public void delete(String oid) {
        List<FunctionalIteration> iterations = iterationMapper.selectByMasterOid(oid);
        for (FunctionalIteration iter : iterations) {
            iterationMapper.deleteByOid(iter.getOid());
        }
        FunctionalMapper.deleteByOid(oid);
    }

    @Override
    public FunctionalEntity findByOid(String oid) {
        return FunctionalMapper.selectByOid(oid);
    }

    @Override
    public List<FunctionalEntity> findByContainerOid(String containerOid) {
        return FunctionalMapper.selectByContainerOid(containerOid);
    }

    @Override
    public List<FunctionalEntity> findByContainerAndStage(String containerOid, String stageOid) {
        return FunctionalMapper.selectByContainerAndStage(containerOid, stageOid);
    }

    @Override
    public List<FunctionalEntity> findByFolderOid(String folderOid) {
        return FunctionalMapper.selectByFolderOid(folderOid);
    }

    @Override
    public List<FunctionalVO> findVOsByFolder(String folderOid) {
        List<FunctionalEntity> entities = FunctionalMapper.selectByFolderOid(folderOid);
        List<FunctionalVO> vos = new ArrayList<>();
        for (FunctionalEntity entity : entities) {
            FunctionalVO vo = new FunctionalVO();
            vo.setOid(entity.getOid());
            vo.setName(entity.getName());
            vo.setNumber(entity.getNumber());
            vo.setDescription(entity.getDescription());
            vo.setTypeDefinitionCode(entity.getTypeDefinitionCode());
            vo.setFolderOid(entity.getFolderOid());
            vo.setStageOid(entity.getStageOid());
            vo.setContainerOid(entity.getContainerOid());
            vo.setContainerType(entity.getContainerType());

            if (entity.getTypeDefinitionCode() != null) {
                TypeDefinition td = typeDefinitionMapper.selectByCode(entity.getTypeDefinitionCode(), null, null);
                if (td != null) vo.setTypeDefinitionName(td.getName());
            }

            FunctionalIteration latestIter = iterationMapper.selectLatestByMasterOid(entity.getOid());
            if (latestIter != null) {
                vo.setIterationOid(latestIter.getOid());
                vo.setRevision(latestIter.getRevision());
                vo.setIteration(latestIter.getIteration());
                vo.setDisplayVersion(latestIter.getDisplayVersion());
                vo.setView(latestIter.getView());
                vo.setStatus(latestIter.getStatus());
                vo.setCheckedOut(latestIter.isCheckedOut());
                vo.setCheckedOutBy(latestIter.getCheckedOutBy());
                vo.setCheckedOutComment(latestIter.getCheckedOutComment());
                vo.setLatest(latestIter.isLatest());
            }

            vo.setCreator(entity.getCreator());
            if (entity.getCreatedAt() != null) vo.setCreatedAt(entity.getCreatedAt().toString());

            vos.add(vo);
        }
        return vos;
    }
}
