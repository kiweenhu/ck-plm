package cn.ck.plm.document.service.impl;

import cn.ck.plm.document.dto.DocumentVO;
import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.entity.DocumentIteration;
import cn.ck.plm.document.mapper.DocumentMapper;
import cn.ck.plm.document.mapper.DocumentIterationMapper;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.cls.service.impl.ClsIbaDataSupport;
import cn.ck.plm.document.service.api.DocumentService;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.base.service.impl.RevisionRuleGuard;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentServiceImpl implements DocumentService, SoftTypeInstanceCapability {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    /** 能力宿主 code（= type_definition.root_type_code） */
    private static final String HOST = "DOCUMENT";

    /** 实体 IBA 属性归属的实体类型编码 */
    private static final String IBA_ENTITY_TYPE = "DOCUMENT";

    private final DocumentMapper documentMapper;
    private final DocumentIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final NumberService numberService;
    private final VersionRuleService versionRuleService;
    private final RevisionRuleGuard revisionRuleGuard;
    private final LifecycleTemplateService lifecycleTemplateService;
    /** 状态 code → 显示名（列表/详情都要给"人看得懂"的那个字，见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;
    private final IbaDataSupport ibaDataSupport;
    private final ClsIbaDataSupport clsIbaDataSupport;
    private final ClsIbaDataService clsIbaDataService;
    private final ObjectMapper objectMapper;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                                DocumentIterationMapper iterationMapper,
                                TypeDefinitionMapper typeDefinitionMapper,
                                NumberService numberService,
                                VersionRuleService versionRuleService,
                                RevisionRuleGuard revisionRuleGuard,
                                LifecycleTemplateService lifecycleTemplateService,
                                LifecycleStatusService lifecycleStatusService,
                                IbaDataSupport ibaDataSupport,
                                ClsIbaDataSupport clsIbaDataSupport,
                                ClsIbaDataService clsIbaDataService,
                                ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
        this.versionRuleService = versionRuleService;
        this.revisionRuleGuard = revisionRuleGuard;
        this.lifecycleTemplateService = lifecycleTemplateService;
        this.lifecycleStatusService = lifecycleStatusService;
        this.ibaDataSupport = ibaDataSupport;
        this.clsIbaDataSupport = clsIbaDataSupport;
        this.clsIbaDataService = clsIbaDataService;
        this.objectMapper = objectMapper;
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
        Document document = objectMapper.convertValue(payload, Document.class);
        document.setTypeDefinitionCode(type.getCode());

        String ckfileOid = ibaDataSupport.getString(payload, "ckfileOid");
        String attachmentOid = ibaDataSupport.getString(payload, "attachmentOid");

        Document created = create(document, ckfileOid, attachmentOid);

        DocumentIteration latest = findLatestIteration(created.getOid());
        String iterationOid = latest != null ? latest.getOid() : created.getOid();
        clsIbaDataSupport.saveClsIbaValues(iterationOid, created.getClsOid(), payload);
        ibaDataSupport.saveIbaValues(IBA_ENTITY_TYPE, created.getOid(), payload);

        SoftTypeInstanceResult result = new SoftTypeInstanceResult();
        result.setOid(created.getOid());
        result.setNumber(created.getNumber());
        result.setName(created.getName());
        result.setIterationOid(iterationOid);
        result.setDisplayVersion(latest != null ? latest.getDisplayVersion() : null);
        result.setEntity(created);
        return result;
    }

    @Override
    public Object getInstance(String oid, Map<String, Object> params) {
        Document doc = findByOid(oid);
        if (doc == null) {
            return null;
        }
        Map<String, Object> result = objectMapper.convertValue(doc,
                new TypeReference<Map<String, Object>>() {});
        DocumentIteration latest = findLatestIteration(oid);
        // 版本字段：与原生 GET 端点 / 其他宿主（PART、ENG_DOCUMENT）保持一致 ——
        // 调用方（如流程发起时记"业务对象大版本"）按这些键取当前版本，缺了就取不到
        if (latest != null) {
            result.put("iterationOid", latest.getOid());
            result.put("revision", latest.getRevision());
            result.put("iteration", latest.getIteration());
            result.put("displayVersion", latest.getDisplayVersion());
            // 检出与主文件是迭代级字段，主档上没有：任务中心「查看详情」直接复用文档详情抽屉，
            // 缺了它们抽屉会把"已检出 + 有主文件"显示成"已检入 + 暂无主文件"
            result.put("checkedOut", latest.isCheckedOut());
            result.put("checkedOutBy", latest.getCheckedOutBy());
            result.put("checkedOutComment", latest.getCheckedOutComment());
            result.put("ckfileOid", latest.getCkfileOid());
            // 状态必须一并返回：Part / EngDoc 的 getInstance 都给了，文档缺这两键 →
            // 「设置生命周期状态」弹窗读到 null，把有状态的对象显示成"（无状态）"，
            // 候选的可达性也跟着按"无状态"算错
            if (latest.getStatus() != null) {
                result.put("statusCode", latest.getStatus().getCode());
                result.put("statusName", lifecycleStatusService.displayName(
                        latest.getLifecycleTemplateIterationOid(), latest.getStatus().getCode()));
            }
        }
        // 附加最新迭代的分类 IBA 属性值（平铺到顶层，与原生端点一致）
        if (doc.getClsOid() != null && latest != null) {
            Map<String, Object> clsIba = clsIbaDataService.getValues(latest.getOid(), doc.getClsOid());
            if (clsIba != null) {
                result.putAll(clsIba);
            }
        }
        return result;
    }

    @Override
    public Object updateInstance(String oid, Map<String, Object> body) {
        Document document = objectMapper.convertValue(body, Document.class);
        document.setOid(oid);
        Document updated = update(document);
        // 分类 IBA（迭代级，entity_oid = 最新迭代 oid）
        DocumentIteration latest = findLatestIteration(oid);
        String iterOid = latest != null ? latest.getOid() : oid;
        clsIbaDataSupport.saveClsIbaValues(iterOid, updated.getClsOid(), body);
        // 实体 IBA（合并保存，保留未提交字段）
        ibaDataSupport.mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
        return updated;
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
    public Document rename(String oid, String name) {
        Document existing = documentMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("文档不存在: " + oid);
        }
        existing.setName(name);
        existing.setUpdatedAt(LocalDateTime.now());
        documentMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public Document move(String oid, String containerOid, String containerType, String folderOid, String stageOid) {
        Document existing = documentMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("文档不存在: " + oid);
        }
        existing.setContainerOid(normalizeOid(containerOid));
        existing.setContainerType(containerType);
        existing.setFolderOid(normalizeOid(folderOid));
        existing.setStageOid(normalizeOid(stageOid));
        existing.setUpdatedAt(LocalDateTime.now());
        documentMapper.update(existing);
        return existing;
    }

    /** 空字符串外键 oid 转 null，避免违反外键约束 */
    private String normalizeOid(String oid) {
        if (oid == null) return null;
        String trimmed = oid.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        documentMapper.deleteByOid(oid);
    }

    @Override
    @Transactional
    public void deleteLatestIteration(String oid) {
        DocumentIteration latest = iterationMapper.selectLatestByMasterOid(oid);
        if (latest == null) {
            return;
        }
        iterationMapper.deleteByOid(latest.getOid());

        List<DocumentIteration> remaining = iterationMapper.selectByMasterOid(oid);
        if (remaining == null || remaining.isEmpty()) {
            // 无剩余版本，删除主对象
            documentMapper.deleteByOid(oid);
            return;
        }
        // 将剩余中最新的版本标记为 latest=true
        DocumentIteration newLatest = remaining.get(0);
        if (!newLatest.isLatest()) {
            newLatest.setLatest(true);
            iterationMapper.update(newLatest);
        }
    }

    @Override
    @Transactional
    public void setLifecycleStateInstance(String oid, String entityVersion, String targetStateCode,
                                          String migrateToTemplateIterationOid) {
        Document document = documentMapper.selectByOid(oid);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + oid);
        }
        DocumentIteration iter = resolveVersion(oid, entityVersion);
        // 同一个事务里先迁模板再设状态：中间失败一起回滚，不会出现"迁了模板、状态没设上"
        if (migrateToTemplateIterationOid != null && !migrateToTemplateIterationOid.trim().isEmpty()) {
            lifecycleTemplateService.rebindLifecycle(iter, migrateToTemplateIterationOid);
        }
        lifecycleTemplateService.moveToState(iter, targetStateCode);
        iter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iter);
        log.info("设置生命周期状态: documentOid={}, version={}.{}, status={}", oid,
                iter.getRevision(), iter.getIteration(),
                iter.getStatus() != null ? iter.getStatus().getCode() : null);
    }

    @Override
    @Transactional
    public void resetLifecycleStateInstance(String oid, String entityVersion,
                                            String migrateToTemplateIterationOid) {
        Document document = documentMapper.selectByOid(oid);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + oid);
        }
        DocumentIteration iter = resolveVersion(oid, entityVersion);
        if (migrateToTemplateIterationOid != null && !migrateToTemplateIterationOid.trim().isEmpty()) {
            lifecycleTemplateService.rebindLifecycle(iter, migrateToTemplateIterationOid);
        }
        lifecycleTemplateService.moveToInitialState(iter);
        iter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iter);
        log.info("退回初始状态: documentOid={}, version={}.{}, status={}", oid,
                iter.getRevision(), iter.getIteration(),
                iter.getStatus() != null ? iter.getStatus().getCode() : null);
    }

    @Override
    public String lifecycleTemplateIterationOfInstance(String oid, String entityVersion) {
        return resolveVersion(oid, entityVersion).getLifecycleTemplateIterationOid();
    }

    /**
     * 取「该大版本当前的最新小版本」—— 流程针对的是大版本，落到具体版本时统一按这个口径解析。
     *
     * <p>{@code selectByMasterOid} 已按 revision、iteration 降序，故同大版本里第一条即最新小版本。
     */
    private DocumentIteration resolveVersion(String masterOid, String entityVersion) {
        List<DocumentIteration> all = iterationMapper.selectByMasterOid(masterOid);
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("文档没有可用版本: " + masterOid);
        }
        String want = entityVersion == null ? "" : entityVersion.trim();
        if (want.isEmpty()) {
            return all.get(0);
        }
        for (DocumentIteration iter : all) {
            if (want.equalsIgnoreCase(iter.getRevision())) {
                return iter;
            }
        }
        throw new IllegalStateException("文档 " + masterOid + " 不存在大版本 " + want);
    }

    /** 新建视图版本（统一入口形态）：转调本宿主已有实现，供流程「object.promote」使用 */
    @Override
    @Transactional
    public void newViewVersionInstance(String oid) {
        newViewVersion(oid);
    }

    @Override
    @Transactional
    public void newViewVersion(String oid) {
        Document document = documentMapper.selectByOid(oid);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + oid);
        }
        DocumentIteration currentIter = iterationMapper.selectLatestByMasterOid(oid);
        if (currentIter == null) {
            throw new IllegalArgumentException("文档没有可用版本: " + oid);
        }
        // 检出中的工作副本不能作为修订基线（同部件：避免复制未定稿内容并留下孤版本）
        if (currentIter.isCheckedOut()) {
            throw new IllegalStateException(
                    "文档已被 " + currentIter.getCheckedOutBy() + " 检出，请先检入或取消检出后再修订");
        }
        // 版本规则用尽时不再回退字符顺延：通知租户管理员改规则，并阻止修订
        revisionRuleGuard.ensureRevisionAllowed(document.getTypeDefinitionCode(), currentIter.getRevision(),
                document.getNumber(), document.getName());
        // 当前生命周期状态是否允许修订（模板状态上的 revisable，配置缺失不阻断）
        lifecycleTemplateService.ensureRevisable(currentIter);

        // 1. 原最新版本 latest → false
        currentIter.setLatest(false);
        iterationMapper.update(currentIter);

        // 2. 创建新的大版本（revision+1，iteration=1，view 继承）
        DocumentIteration copy = new DocumentIteration();
        copy.setOid(java.util.UUID.randomUUID().toString());
        copy.setMasterOid(oid);
        copy.setIteration(1);
        copy.setLatest(true);
        copy.setCheckedOut(false);
        copy.setCkfileOid(currentIter.getCkfileOid());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy.setCreator(currentIter.getCreator());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        // 修订出的新大版本要重新初始化生命周期：回到该对象固化模板的初始状态，不继承旧版本的状态。
        // 未绑定模板（历史数据）时保持原状态，避免把"本来就没有生命周期"的对象改坏
        LifecycleStatus initialStatus = lifecycleTemplateService.initialStatus(
                currentIter.getLifecycleTemplateIterationOid());
        copy.setStatus(initialStatus != null ? initialStatus : currentIter.getStatus());
        if (initialStatus == null && currentIter.getLifecycleTemplateIterationOid() != null) {
            log.warn("修订未能解析模板初始状态，沿用原状态: docOid={}, templateIteration={}",
                    oid, currentIter.getLifecycleTemplateIterationOid());
        }

        String versionRuleCode = versionRuleService.resolveVersionRuleCode(document.getTypeDefinitionCode());
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

        log.info("新建视图版本成功: docOid={}, {}.{} -> {}.1", oid,
                currentIter.getRevision(), currentIter.getIteration(), copy.getRevision());
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
        // 同一文件夹的文档通常共用模板：按模板子版本缓存"状态 → 可修订"，避免逐行查状态表
        Map<String, Map<String, Boolean>> revisableCache = new HashMap<>();
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
                    // 旁边的 code 只是标识，界面显示要用显示名（草稿/已发布）
                    vo.setStatusName(lifecycleStatusService.displayName(
                            latestIter.getLifecycleTemplateIterationOid(), latestIter.getStatus().getCode()));
                    // 行内「修订」是否出现，由该状态在模板里的 revisable 决定（未知按允许）
                    Map<String, Boolean> flags = revisableCache.computeIfAbsent(
                            latestIter.getLifecycleTemplateIterationOid(),
                            lifecycleTemplateService::revisableFlags);
                    vo.setRevisable(flags.getOrDefault(latestIter.getStatus().getCode(), true));
                }
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<DocumentIteration> findIterationsByMaster(String masterOid) {
        return iterationMapper.selectByMasterOid(masterOid);
    }

    @Override
    public DocumentIteration findLatestIteration(String masterOid) {
        return iterationMapper.selectLatestByMasterOid(masterOid);
    }

    @Override
    public void updateFrom(MasterEntity target, MasterEntity source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getNumber() != null) target.setNumber(source.getNumber());
        if (source.getDescription() != null) target.setDescription(source.getDescription());
    }
}
