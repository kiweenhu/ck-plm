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
import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.base.service.api.ViewService;
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
    private final ViewService viewService;

    public PartServiceImpl(PartMapper partMapper,
                            PartIterationMapper iterationMapper,
                            TypeDefinitionMapper typeDefinitionMapper,
                            NumberService numberService,
                            VersionRuleService versionRuleService,
                            LifecycleTemplateService lifecycleTemplateService,
                            ViewService viewService) {
        this.partMapper = partMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
        this.versionRuleService = versionRuleService;
        this.lifecycleTemplateService = lifecycleTemplateService;
        this.viewService = viewService;
    }

    /** 获取默认视图（Design），不存在时返回 null */
    private View defaultView() {
        try {
            return viewService.findByCode("Design");
        } catch (Exception e) {
            return null;
        }
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
        iter.setBranchId("master");
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
        derived.setBranchId(src.getBranchId() != null ? src.getBranchId() : "master");
        derived.setCreatedAt(LocalDateTime.now());
        derived.setUpdatedAt(LocalDateTime.now());
        derived.setUnit(src.getUnit());
        derived.setSource(src.getSource());
        iterationMapper.insert(derived);
        return derived;
    }

    @Override
    @Transactional
    public Part create(Part part, String ckfileOid, String attachmentOid, String unit, String source) {
        if (part.getOid() == null || part.getOid().isEmpty()) {
            part.setOid(java.util.UUID.randomUUID().toString());
        }
        if (part.getCreatedAt() == null) {
            part.setCreatedAt(LocalDateTime.now());
        }
        if (part.getUpdatedAt() == null) {
            part.setUpdatedAt(LocalDateTime.now());
        }

        // 外键字段空字符串转 null，避免违反外键约束
        part.setClsOid(normalizeOid(part.getClsOid()));
        part.setContainerOid(normalizeOid(part.getContainerOid()));
        part.setFolderOid(normalizeOid(part.getFolderOid()));
        part.setStageOid(normalizeOid(part.getStageOid()));

        numberService.generateNumberIfNeeded(part, part.getTypeDefinitionCode());
        partMapper.insert(part);

        PartIteration iter = new PartIteration();
        iter.setOid(java.util.UUID.randomUUID().toString());
        iter.setMasterOid(part.getOid());
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setBranchId("master");
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(part.getCreator());
        iter.setUnit(unit);
        iter.setSource(source);
        iter.setView(defaultView());

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
        existing.setClsOid(part.getClsOid());
        existing.setUpdatedAt(LocalDateTime.now());
        partMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public Part rename(String oid, String name) {
        Part existing = partMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("部件不存在: " + oid);
        }
        existing.setName(name);
        existing.setUpdatedAt(LocalDateTime.now());
        partMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public Part saveAs(String sourceOid, String newName) {
        Part source = partMapper.selectByOid(sourceOid);
        if (source == null) {
            throw new IllegalArgumentException("部件不存在: " + sourceOid);
        }

        // 1. 创建新主对象（复制基础字段，生成新编号）
        Part copy = new Part();
        copy.setOid(java.util.UUID.randomUUID().toString());
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        copy.setTypeDefinitionCode(source.getTypeDefinitionCode());
        copy.setContainerOid(source.getContainerOid());
        copy.setContainerType(source.getContainerType());
        copy.setFolderOid(source.getFolderOid());
        copy.setStageOid(source.getStageOid());
        copy.setClsOid(source.getClsOid());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy.setCreator(source.getCreator());
        numberService.generateNumberIfNeeded(copy, copy.getTypeDefinitionCode());
        partMapper.insert(copy);

        // 2. 复制最新 iteration 到新主对象（初始 iteration=1，复制 unit/source/view）
        PartIteration sourceIter = iterationMapper.selectLatestByMasterOid(sourceOid);
        PartIteration iter = new PartIteration();
        iter.setOid(java.util.UUID.randomUUID().toString());
        iter.setMasterOid(copy.getOid());
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setBranchId("master");
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(source.getCreator());
        if (sourceIter != null) {
            iter.setUnit(sourceIter.getUnit());
            iter.setSource(sourceIter.getSource());
            iter.setView(sourceIter.getView() != null ? sourceIter.getView() : defaultView());
        } else {
            iter.setView(defaultView());
        }

        String versionRuleCode = versionRuleService.resolveVersionRuleCode(copy.getTypeDefinitionCode());
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

        lifecycleTemplateService.initLifecycle(iter, copy.getTypeDefinitionCode());
        iterationMapper.insert(iter);

        log.info("另存为成功: sourceOid={}, newOid={}, name={}", sourceOid, copy.getOid(), newName);
        return copy;
    }

    @Override
    @Transactional
    public Part move(String oid, String containerOid, String containerType, String folderOid, String stageOid) {
        Part existing = partMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("部件不存在: " + oid);
        }
        existing.setContainerOid(normalizeOid(containerOid));
        existing.setContainerType(containerType);
        existing.setFolderOid(normalizeOid(folderOid));
        existing.setStageOid(normalizeOid(stageOid));
        existing.setUpdatedAt(LocalDateTime.now());
        partMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void updateLatestIterationAttributes(String masterOid, String unit, String source) {
        PartIteration latest = iterationMapper.selectLatestByMasterOid(masterOid);
        if (latest == null) {
            return;
        }
        latest.setUnit(unit);
        latest.setSource(source);
        latest.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(latest);
    }

    @Override
    @Transactional
    public void delete(String oid) {
        partMapper.deleteByOid(oid);
    }

    @Override
    @Transactional
    public void deleteLatestIteration(String oid) {
        PartIteration latest = iterationMapper.selectLatestByMasterOid(oid);
        if (latest == null) {
            return;
        }
        iterationMapper.deleteByOid(latest.getOid());

        List<PartIteration> remaining = iterationMapper.selectByMasterOid(oid);
        if (remaining == null || remaining.isEmpty()) {
            // 无剩余版本，删除主对象
            partMapper.deleteByOid(oid);
            return;
        }
        // 将剩余中最新的版本标记为 latest=true
        PartIteration newLatest = remaining.get(0);
        if (!newLatest.isLatest()) {
            newLatest.setLatest(true);
            iterationMapper.update(newLatest);
        }
    }

    @Override
    @Transactional
    public void newViewVersion(String oid) {
        Part part = partMapper.selectByOid(oid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + oid);
        }
        PartIteration currentIter = iterationMapper.selectLatestByMasterOid(oid);
        if (currentIter == null) {
            throw new IllegalArgumentException("部件没有可用版本: " + oid);
        }

        // 1. 原最新版本 latest → false
        currentIter.setLatest(false);
        iterationMapper.update(currentIter);

        // 2. 创建新的大版本（revision+1，iteration=1，view 继承）
        PartIteration copy = new PartIteration();
        copy.setOid(java.util.UUID.randomUUID().toString());
        copy.setMasterOid(oid);
        copy.setIteration(1);
        copy.setLatest(true);
        copy.setCheckedOut(false);
        copy.setBranchId(currentIter.getBranchId() != null ? currentIter.getBranchId() : "master");
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy.setCreator(currentIter.getCreator());
        copy.setView(currentIter.getView() != null ? currentIter.getView() : defaultView());
        copy.setStatus(currentIter.getStatus());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        copy.setUnit(currentIter.getUnit());
        copy.setSource(currentIter.getSource());

        String versionRuleCode = versionRuleService.resolveVersionRuleCode(part.getTypeDefinitionCode());
        String nextRevision = null;
        if (versionRuleCode != null) {
            try {
                nextRevision = versionRuleService.getNextRevision(versionRuleCode, currentIter.getRevision());
            } catch (Exception e) {
                log.warn("版本规则获取下一版本失败，使用 char+1: ruleCode={}, error={}", versionRuleCode, e.getMessage());
            }
        }
        if (nextRevision == null) {
            char c = currentIter.getRevision() != null && !currentIter.getRevision().isEmpty()
                    ? currentIter.getRevision().charAt(0) : 'A';
            nextRevision = String.valueOf((char) (c + 1));
        }
        copy.setRevision(nextRevision);
        iterationMapper.insert(copy);

        log.info("新建视图版本成功: partOid={}, {}.{} -> {}.1", oid,
                currentIter.getRevision(), currentIter.getIteration(), copy.getRevision());
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
    public List<Part> findByContainerAndKeyword(String containerOid, String keyword) {
        return partMapper.selectByContainerAndKeyword(containerOid, keyword);
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
            vo.setClsOid(part.getClsOid());
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
            vo.setClsOid(part.getClsOid());
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
    public List<PartIteration> findIterationsByMaster(String masterOid) {
        return iterationMapper.selectByMasterOid(masterOid);
    }

    @Override
    public PartIteration findLatestIteration(String masterOid) {
        return iterationMapper.selectLatestByMasterOid(masterOid);
    }

    @Override
    public PartIteration findIterationByOid(String iterationOid) {
        return iterationMapper.selectByOid(iterationOid);
    }

    @Override
    public void updateFrom(MasterEntity target, MasterEntity source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getNumber() != null) target.setNumber(source.getNumber());
        if (source.getDescription() != null) target.setDescription(source.getDescription());
    }

    /** 空字符串外键 oid 转 null，避免违反外键约束 */
    private String normalizeOid(String oid) {
        if (oid == null) return null;
        String trimmed = oid.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
