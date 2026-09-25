/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.service.impl;

import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import cn.ck.plm.functional.dto.FunctionalVO;
import cn.ck.plm.functional.entity.FunctionalEntity;
import cn.ck.plm.functional.entity.FunctionalIteration;
import cn.ck.plm.functional.mapper.FunctionalIterationMapper;
import cn.ck.plm.functional.mapper.FunctionalMapper;
import cn.ck.plm.functional.service.api.FunctionalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FunctionalServiceImpl implements FunctionalService, SoftTypeInstanceCapability {

    /** 能力宿主 code（= type_definition.root_type_code） */
    private static final String HOST = "FUNCTIONAL";

    private final FunctionalMapper FunctionalMapper;
    private final FunctionalIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final NumberService numberService;
    private final IbaDataSupport ibaDataSupport;
    private final ObjectMapper objectMapper;
    /** 状态 code → 显示名（见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;

    public FunctionalServiceImpl(FunctionalMapper FunctionalMapper,
                             FunctionalIterationMapper iterationMapper,
                             TypeDefinitionMapper typeDefinitionMapper,
                             NumberService numberService,
                             IbaDataSupport ibaDataSupport,
                             ObjectMapper objectMapper,
                             LifecycleStatusService lifecycleStatusService) {
        this.FunctionalMapper = FunctionalMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
        this.ibaDataSupport = ibaDataSupport;
        this.objectMapper = objectMapper;
        this.lifecycleStatusService = lifecycleStatusService;
    }

    // ==================== 能力宿主策略（SoftTypeInstanceCapability）====================

    @Override
    public String hostCode() {
        return HOST;
    }

    @Override
    public Set<SoftTypeInstanceCapability.Operation> supportedOperations() {
        return EnumSet.of(
                SoftTypeInstanceCapability.Operation.CREATE,
                SoftTypeInstanceCapability.Operation.READ,
                SoftTypeInstanceCapability.Operation.UPDATE);
    }

    @Override
    public SoftTypeInstanceResult createInstance(TypeDefinition type, Map<String, Object> payload) {
        FunctionalEntity entity = objectMapper.convertValue(payload, FunctionalEntity.class);
        entity.setTypeDefinitionCode(type.getCode());

        String ckfileOid = ibaDataSupport.getString(payload, "ckfileOid");
        String attachmentOid = ibaDataSupport.getString(payload, "attachmentOid");

        FunctionalEntity created = create(entity, ckfileOid, attachmentOid);
        ibaDataSupport.saveIbaValues(HOST, created.getOid(), payload);

        SoftTypeInstanceResult result = new SoftTypeInstanceResult();
        result.setOid(created.getOid());
        result.setNumber(created.getNumber());
        result.setName(created.getName());
        result.setEntity(created);
        return result;
    }

    @Override
    public Object getInstance(String oid, Map<String, Object> params) {
        FunctionalEntity entity = findByOid(oid);
        if (entity == null) {
            return null;
        }
        Map<String, Object> result = objectMapper.convertValue(entity,
                new TypeReference<Map<String, Object>>() {});
        // 版本字段：与原生 GET 端点 / 其他宿主（PART、DOCUMENT、ENG_DOCUMENT）保持一致 ——
        // 调用方（如流程发起时记"业务对象大版本"）按这些键取当前版本，缺了就取不到
        FunctionalIteration latest = iterationMapper.selectLatestByMasterOid(oid);
        if (latest != null) {
            result.put("iterationOid", latest.getOid());
            result.put("revision", latest.getRevision());
            result.put("iteration", latest.getIteration());
            result.put("displayVersion", latest.getDisplayVersion());
        }
        return result;
    }

    @Override
    public Object updateInstance(String oid, Map<String, Object> body) {
        FunctionalEntity entity = objectMapper.convertValue(body, FunctionalEntity.class);
        entity.setOid(oid);
        return update(entity);
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
                // 前端读的是 status.displayName；迭代上的 status 只带 code（LifecycleStatusTypeHandler），
                // 不在这里补显示名，列表就会显示 code
                if (latestIter.getStatus() != null) {
                    latestIter.getStatus().setDisplayName(lifecycleStatusService.displayName(
                            latestIter.getLifecycleTemplateIterationOid(), latestIter.getStatus().getCode()));
                }
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
