package cn.ck.plm.part.service.impl;

import cn.ck.plm.part.dto.PartVO;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartMapper;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.service.api.PartService;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PartServiceImpl implements PartService {

    private static final Logger log = LoggerFactory.getLogger(PartServiceImpl.class);

    private final PartMapper partMapper;
    private final PartIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final NumberService numberService;
    private final VersionRuleService versionRuleService;
    private final LifecycleTemplateService lifecycleTemplateService;

    public PartServiceImpl(PartMapper partMapper,
                            PartIterationMapper iterationMapper,
                            TypeDefinitionMapper typeDefinitionMapper,
                            NumberService numberService,
                            VersionRuleService versionRuleService,
                            LifecycleTemplateService lifecycleTemplateService) {
        this.partMapper = partMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
        this.versionRuleService = versionRuleService;
        this.lifecycleTemplateService = lifecycleTemplateService;
    }

    @Override
    public IterationEntity createInitialIteration(MasterEntity master) {
        PartIteration iter = new PartIteration();
        iter.setOid(java.util.UUID.randomUUID().toString());
        iter.setMasterOid(master.getOid());
        iter.setRevision("A");
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(master.getCreator());
        iterationMapper.insert(iter);
        return iter;
    }

    @Override
    public IterationEntity createDerivedIteration(MasterEntity master, IterationEntity source) {
        PartIteration src = (PartIteration) source;
        PartIteration derived = new PartIteration();
        derived.setOid(java.util.UUID.randomUUID().toString());
        derived.setMasterOid(master.getOid());
        derived.setRevision(src.getRevision());
        derived.setIteration(src.getIteration());
        derived.setLatest(false);
        derived.setCheckedOut(false);
        derived.setDerivedFromOid(src.getOid());
        derived.setDerivedAt(LocalDateTime.now());
        derived.setCreatedAt(LocalDateTime.now());
        derived.setUpdatedAt(LocalDateTime.now());
        derived.setUnit(src.getUnit());
        derived.setSource(src.getSource());
        iterationMapper.insert(derived);
        return derived;
    }

    @Override
    @Transactional
    public Part create(Part part, String ckfileOid, String attachmentOid) {
        if (part.getOid() == null || part.getOid().isEmpty()) {
            part.setOid(java.util.UUID.randomUUID().toString());
        }
        if (part.getCreatedAt() == null) {
            part.setCreatedAt(LocalDateTime.now());
        }
        if (part.getUpdatedAt() == null) {
            part.setUpdatedAt(LocalDateTime.now());
        }

        numberService.generateNumberIfNeeded(part, part.getTypeDefinitionCode());
        partMapper.insert(part);

        PartIteration iter = new PartIteration();
        iter.setOid(java.util.UUID.randomUUID().toString());
        iter.setMasterOid(part.getOid());
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(part.getCreator());

        String versionRuleCode = versionRuleService.resolveVersionRuleCode(part.getTypeDefinitionCode());
        if (versionRuleCode != null) {
            try {
                iter.setRevision(versionRuleService.getFirstRevision(versionRuleCode));
            } catch (Exception e) {
                log.warn("版本规则获取失败，使用默认 A: ruleCode={}, error={}", versionRuleCode, e.getMessage());
                iter.setRevision("A");
            }
        } else {
            iter.setRevision("A");
        }

        lifecycleTemplateService.initLifecycle(iter, part.getTypeDefinitionCode());
        iterationMapper.insert(iter);

        return part;
    }

    @Override
    @Transactional
    public Part update(Part part) {
        Part existing = partMapper.selectByOid(part.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("部件不存在: " + part.getOid());
        }
        existing.setTypeDefinitionCode(part.getTypeDefinitionCode());
        existing.setContainerOid(part.getContainerOid());
        existing.setContainerType(part.getContainerType());
        existing.setFolderOid(part.getFolderOid());
        existing.setStageOid(part.getStageOid());
        existing.setClassificationOid(part.getClassificationOid());
        existing.setUpdatedAt(LocalDateTime.now());
        partMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        partMapper.deleteByOid(oid);
    }

    @Override
    public Part findByOid(String oid) {
        return partMapper.selectByOid(oid);
    }

    @Override
    public List<Part> findByContainerOid(String containerOid) {
        return partMapper.selectByContainerOid(containerOid);
    }

    @Override
    public List<Part> findByContainerAndStage(String containerOid, String stageOid) {
        return partMapper.selectByContainerAndStage(containerOid, stageOid);
    }

    @Override
    public List<Part> findByFolder(String folderOid) {
        return partMapper.selectByFolderOid(folderOid);
    }

    @Override
    public List<Part> findByClassification(String classificationOid) {
        return partMapper.selectByClassificationOid(classificationOid);
    }

    @Override
    public List<PartVO> findVOsByFolder(String folderOid) {
        List<Part> parts = partMapper.selectByFolderOid(folderOid);
        List<PartVO> vos = new ArrayList<>();
        for (Part part : parts) {
            PartVO vo = new PartVO();
            vo.setOid(part.getOid());
            vo.setName(part.getName());
            vo.setDescription(part.getDescription());
            vo.setCode(part.getNumber());
            vo.setNumber(part.getNumber());
            vo.setTypeDefinitionCode(part.getTypeDefinitionCode());
            vo.setContainerOid(part.getContainerOid());
            vo.setContainerType(part.getContainerType());
            vo.setFolderOid(part.getFolderOid());
            vo.setStageOid(part.getStageOid());
            vo.setClassificationOid(part.getClassificationOid());
            vo.setCreator(part.getCreator());
            vo.setCreatedAt(part.getCreatedAt() != null ? part.getCreatedAt().toString() : null);
            vo.setUpdater(part.getUpdater());
            vo.setUpdatedAt(part.getUpdatedAt() != null ? part.getUpdatedAt().toString() : null);

            TypeDefinition td = typeDefinitionMapper.selectByCode(part.getTypeDefinitionCode(), part.getTenantOid(), null);
            if (td != null) {
                vo.setTypeDefinitionName(td.getName());
            }

            PartIteration latestIter = iterationMapper.selectLatestByMasterOid(part.getOid());
            if (latestIter != null) {
                vo.setIterationOid(latestIter.getOid());
                vo.setRevision(latestIter.getRevision());
                vo.setIteration(latestIter.getIteration());
                vo.setDisplayVersion(latestIter.getDisplayVersion());
                vo.setCheckedOut(latestIter.isCheckedOut());
                vo.setCheckedOutBy(latestIter.getCheckedOutBy());
                vo.setCheckedOutComment(latestIter.getCheckedOutComment());
                vo.setLatest(latestIter.isLatest());
                if (latestIter.getStatus() != null) {
                    vo.setStatusCode(latestIter.getStatus().getCode());
                }
                vo.setUnit(latestIter.getUnit());
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<PartVO> findVOsByClassification(String classificationOid) {
        List<Part> parts = partMapper.selectByClassificationOid(classificationOid);
        List<PartVO> vos = new ArrayList<>();
        for (Part part : parts) {
            PartVO vo = new PartVO();
            vo.setOid(part.getOid());
            vo.setName(part.getName());
            vo.setDescription(part.getDescription());
            vo.setCode(part.getNumber());
            vo.setNumber(part.getNumber());
            vo.setTypeDefinitionCode(part.getTypeDefinitionCode());
            vo.setContainerOid(part.getContainerOid());
            vo.setContainerType(part.getContainerType());
            vo.setFolderOid(part.getFolderOid());
            vo.setStageOid(part.getStageOid());
            vo.setClassificationOid(part.getClassificationOid());
            vo.setCreator(part.getCreator());
            vo.setCreatedAt(part.getCreatedAt() != null ? part.getCreatedAt().toString() : null);
            vo.setUpdater(part.getUpdater());
            vo.setUpdatedAt(part.getUpdatedAt() != null ? part.getUpdatedAt().toString() : null);

            TypeDefinition td = typeDefinitionMapper.selectByCode(part.getTypeDefinitionCode(), part.getTenantOid(), null);
            if (td != null) {
                vo.setTypeDefinitionName(td.getName());
            }

            PartIteration latestIter = iterationMapper.selectLatestByMasterOid(part.getOid());
            if (latestIter != null) {
                vo.setIterationOid(latestIter.getOid());
                vo.setRevision(latestIter.getRevision());
                vo.setIteration(latestIter.getIteration());
                vo.setDisplayVersion(latestIter.getDisplayVersion());
                vo.setCheckedOut(latestIter.isCheckedOut());
                vo.setCheckedOutBy(latestIter.getCheckedOutBy());
                vo.setCheckedOutComment(latestIter.getCheckedOutComment());
                vo.setLatest(latestIter.isLatest());
                if (latestIter.getStatus() != null) {
                    vo.setStatusCode(latestIter.getStatus().getCode());
                }
                vo.setUnit(latestIter.getUnit());
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public void updateFrom(MasterEntity target, MasterEntity source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getNumber() != null) target.setNumber(source.getNumber());
        if (source.getDescription() != null) target.setDescription(source.getDescription());
    }
}
