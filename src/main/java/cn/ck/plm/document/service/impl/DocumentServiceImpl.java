package cn.ck.plm.document.service.impl;

import cn.ck.plm.document.dto.DocumentVO;
import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.entity.DocumentIteration;
import cn.ck.plm.document.mapper.DocumentMapper;
import cn.ck.plm.document.mapper.DocumentIterationMapper;
import cn.ck.plm.document.service.api.DocumentService;
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
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentMapper documentMapper;
    private final DocumentIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final NumberService numberService;
    private final VersionRuleService versionRuleService;
    private final LifecycleTemplateService lifecycleTemplateService;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                                DocumentIterationMapper iterationMapper,
                                TypeDefinitionMapper typeDefinitionMapper,
                                NumberService numberService,
                                VersionRuleService versionRuleService,
                                LifecycleTemplateService lifecycleTemplateService) {
        this.documentMapper = documentMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
        this.versionRuleService = versionRuleService;
        this.lifecycleTemplateService = lifecycleTemplateService;
    }

    @Override
    public IterationEntity createInitialIteration(MasterEntity master) {
        DocumentIteration iter = new DocumentIteration();
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
        DocumentIteration src = (DocumentIteration) source;
        DocumentIteration derived = new DocumentIteration();
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
        iterationMapper.insert(derived);
        return derived;
    }

    @Override
    @Transactional
    public Document create(Document document, String ckfileOid, String attachmentOid) {
        if (document.getOid() == null || document.getOid().isEmpty()) {
            document.setOid(java.util.UUID.randomUUID().toString());
        }
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }
        if (document.getUpdatedAt() == null) {
            document.setUpdatedAt(LocalDateTime.now());
        }

        numberService.generateNumberIfNeeded(document, document.getTypeDefinitionCode());
        documentMapper.insert(document);

        DocumentIteration iter = new DocumentIteration();
        iter.setOid(java.util.UUID.randomUUID().toString());
        iter.setMasterOid(document.getOid());
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setCkfileOid(ckfileOid);
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(document.getCreator());

        String versionRuleCode = versionRuleService.resolveVersionRuleCode(document.getTypeDefinitionCode());
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

        lifecycleTemplateService.initLifecycle(iter, document.getTypeDefinitionCode());
        iterationMapper.insert(iter);

        return document;
    }

    @Override
    @Transactional
    public Document update(Document document) {
        Document existing = documentMapper.selectByOid(document.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("文档不存在: " + document.getOid());
        }
        existing.setTypeDefinitionCode(document.getTypeDefinitionCode());
        existing.setContainerOid(document.getContainerOid());
        existing.setContainerType(document.getContainerType());
        existing.setFolderOid(document.getFolderOid());
        existing.setStageOid(document.getStageOid());
        existing.setUpdatedAt(LocalDateTime.now());
        documentMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        documentMapper.deleteByOid(oid);
    }

    @Override
    public Document findByOid(String oid) {
        return documentMapper.selectByOid(oid);
    }

    @Override
    public List<Document> findByContainerOid(String containerOid) {
        return documentMapper.selectByContainerOid(containerOid);
    }

    @Override
    public List<Document> findByContainerAndStage(String containerOid, String stageOid) {
        return documentMapper.selectByContainerAndStage(containerOid, stageOid);
    }

    @Override
    public List<Document> findByFolder(String folderOid) {
        return documentMapper.selectByFolderOid(folderOid);
    }

    @Override
    public List<DocumentVO> findVOsByFolder(String folderOid) {
        List<Document> docs = documentMapper.selectByFolderOid(folderOid);
        List<DocumentVO> vos = new ArrayList<>();
        for (Document doc : docs) {
            DocumentVO vo = new DocumentVO();
            vo.setOid(doc.getOid());
            vo.setName(doc.getName());
            vo.setDescription(doc.getDescription());
            vo.setCode(doc.getNumber());
            vo.setNumber(doc.getNumber());
            vo.setTypeDefinitionCode(doc.getTypeDefinitionCode());
            vo.setContainerOid(doc.getContainerOid());
            vo.setContainerType(doc.getContainerType());
            vo.setFolderOid(doc.getFolderOid());
            vo.setStageOid(doc.getStageOid());
            vo.setCreator(doc.getCreator());
            vo.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
            vo.setUpdater(doc.getUpdater());
            vo.setUpdatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null);

            TypeDefinition td = typeDefinitionMapper.selectByCode(doc.getTypeDefinitionCode(), doc.getTenantOid(), null);
            if (td != null) {
                vo.setTypeDefinitionName(td.getName());
            }

            DocumentIteration latestIter = iterationMapper.selectLatestByMasterOid(doc.getOid());
            if (latestIter != null) {
                vo.setIterationOid(latestIter.getOid());
                vo.setRevision(latestIter.getRevision());
                vo.setIteration(latestIter.getIteration());
                vo.setDisplayVersion(latestIter.getDisplayVersion());
                vo.setCheckedOut(latestIter.isCheckedOut());
                vo.setCheckedOutBy(latestIter.getCheckedOutBy());
                vo.setCheckedOutComment(latestIter.getCheckedOutComment());
                vo.setLatest(latestIter.isLatest());
                vo.setCkfileOid(latestIter.getCkfileOid());
                if (latestIter.getStatus() != null) {
                    vo.setStatusCode(latestIter.getStatus().getCode());
                }
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
