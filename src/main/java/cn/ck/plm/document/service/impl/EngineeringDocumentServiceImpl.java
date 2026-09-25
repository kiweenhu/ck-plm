/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.impl;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.document.dto.EngineeringDocumentVO;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;
import cn.ck.plm.document.mapper.EngineeringDocumentIterationMapper;
import cn.ck.plm.document.mapper.EngineeringDocumentMapper;
import cn.ck.plm.document.service.api.EngineeringDocumentService;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.cls.service.impl.ClsIbaDataSupport;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link EngineeringDocumentService} 默认实现。
 *
 * <p>创建流程与 {@code PartServiceImpl#create} 保持一致的编排顺序：
 * 编号生成 → 主对象落库 → 初始迭代（A.1）→ 版本规则解析 → 生命周期初始化 → 迭代落库。
 * 从而保证工程数据与 Part / Document 在版本、生命周期、编码上行为统一。
 */
@Service
public class EngineeringDocumentServiceImpl implements EngineeringDocumentService, SoftTypeInstanceCapability {

    private static final Logger log = LoggerFactory.getLogger(EngineeringDocumentServiceImpl.class);

    /** 分支标识：主线 */
    private static final String BRANCH_MASTER = "master";

    /** 无版本规则时的兜底大版本 */
    private static final String FALLBACK_REVISION = "A";

    /** 能力宿主 code（= type_definition.root_type_code） */
    private static final String HOST = "ENG_DOCUMENT";

    /** 实体 IBA 属性归属的实体类型编码 */
    private static final String IBA_ENTITY_TYPE = "ENG_DOCUMENT";

    private final EngineeringDocumentMapper engDocumentMapper;
    private final EngineeringDocumentIterationMapper iterationMapper;
    private final NumberService numberService;
    private final VersionRuleService versionRuleService;
    private final LifecycleTemplateService lifecycleTemplateService;
    /** 状态 code → 显示名（详情给"人看得懂"的那个字，见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;
    private final TypeDefinitionService typeDefinitionService;
    private final IbaDataSupport ibaDataSupport;
    private final ClsIbaDataSupport clsIbaDataSupport;
    private final ClsIbaDataService clsIbaDataService;
    private final ObjectMapper objectMapper;

    public EngineeringDocumentServiceImpl(EngineeringDocumentMapper engDocumentMapper,
                                          EngineeringDocumentIterationMapper iterationMapper,
                                          NumberService numberService,
                                          VersionRuleService versionRuleService,
                                          LifecycleTemplateService lifecycleTemplateService,
                                          LifecycleStatusService lifecycleStatusService,
                                          TypeDefinitionService typeDefinitionService,
                                          IbaDataSupport ibaDataSupport,
                                          ClsIbaDataSupport clsIbaDataSupport,
                                          ClsIbaDataService clsIbaDataService,
                                          ObjectMapper objectMapper) {
        this.engDocumentMapper = engDocumentMapper;
        this.iterationMapper = iterationMapper;
        this.numberService = numberService;
        this.versionRuleService = versionRuleService;
        this.lifecycleTemplateService = lifecycleTemplateService;
        this.lifecycleStatusService = lifecycleStatusService;
        this.typeDefinitionService = typeDefinitionService;
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
        EngineeringDocument document = objectMapper.convertValue(payload, EngineeringDocument.class);
        document.setTypeDefinitionCode(type.getCode());

        String ckfileOid = ibaDataSupport.getString(payload, "ckfileOid");
        String attachmentOid = ibaDataSupport.getString(payload, "attachmentOid");
        String cadName = ibaDataSupport.getString(payload, "cadName");
        String cadType = ibaDataSupport.getString(payload, "cadType");
        String cadTool = ibaDataSupport.getString(payload, "cadTool");

        EngineeringDocument created = create(document, ckfileOid, attachmentOid, cadName, cadType, cadTool);

        EngineeringDocumentIteration latest = findLatestIteration(created.getOid());
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
        EngineeringDocument doc = findByOid(oid);
        if (doc == null) {
            return null;
        }
        Map<String, Object> result = objectMapper.convertValue(doc,
                new TypeReference<Map<String, Object>>() {});
        // 指定 iterationOid 时返回该迭代，否则返回最新迭代
        String iterationOid = ibaDataSupport.getString(params, "iterationOid");
        EngineeringDocumentIteration iteration = iterationOid != null
                ? findIterationByOid(iterationOid)
                : findLatestIteration(oid);
        if (iteration != null) {
            result.put("iterationOid", iteration.getOid());
            result.put("revision", iteration.getRevision());
            result.put("iteration", iteration.getIteration());
            result.put("displayVersion", iteration.getDisplayVersion());
            result.put("checkedOut", iteration.isCheckedOut());
            result.put("checkedOutBy", iteration.getCheckedOutBy());
            result.put("checkedOutComment", iteration.getCheckedOutComment());
            // CAD 专有属性与 2D 制图属性（ck_eng_document_iteration 的真实列）
            result.put("ckfileOid", iteration.getCkfileOid());
            result.put("cadName", iteration.getCadName());
            result.put("cadType", iteration.getCadType());
            result.put("cadTool", iteration.getCadTool());
            result.put("sheetSize", iteration.getSheetSize());
            result.put("scale", iteration.getScale());
            result.put("sheetNumber", iteration.getSheetNumber());
            result.put("sheetCount", iteration.getSheetCount());
            result.put("projection", iteration.getProjection());
            result.put("author", iteration.getAuthor());
            result.put("material", iteration.getMaterial());
            result.put("weight", iteration.getWeight());
            if (iteration.getStatus() != null) {
                result.put("statusCode", iteration.getStatus().getCode());
                // 显示名走解析器：迭代上的 status 只带 code，直接 getDisplayName() 永远是 null
                result.put("statusName", lifecycleStatusService.displayName(
                        iteration.getLifecycleTemplateIterationOid(), iteration.getStatus().getCode()));
            }
            // 迭代级分类 IBA 属性值
            if (doc.getClsOid() != null) {
                Map<String, Object> clsIba = clsIbaDataService.getValues(iteration.getOid(), doc.getClsOid());
                if (clsIba != null) {
                    result.put("clsIba", clsIba);
                }
            }
        }
        return result;
    }

    @Override
    public Object updateInstance(String oid, Map<String, Object> body) {
        EngineeringDocument doc = objectMapper.convertValue(body, EngineeringDocument.class);
        doc.setOid(oid);
        EngineeringDocument updated = update(doc);
        // 迭代级 CAD 专有 / 2D 制图属性（真实列，不属于 IBA，必须显式落库）
        EngineeringDocumentIteration attrs =
                objectMapper.convertValue(body, EngineeringDocumentIteration.class);
        updateLatestIterationAttributes(oid, attrs);
        // 分类 IBA（迭代级，entity_oid = 最新迭代 oid）
        EngineeringDocumentIteration latest = findLatestIteration(oid);
        String iterOid = latest != null ? latest.getOid() : oid;
        clsIbaDataSupport.saveClsIbaValues(iterOid, updated.getClsOid(), body);
        // 实体 IBA（合并保存，保留未提交字段）
        ibaDataSupport.mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
        return updated;
    }

    // ==================== 创建 ====================

    @Override
    @Transactional
    public EngineeringDocument create(EngineeringDocument engDocument, String ckfileOid, String attachmentOid,
                                      String cadName, String cadType, String cadTool) {
        if (engDocument.getOid() == null || engDocument.getOid().isEmpty()) {
            engDocument.setOid(UUID.randomUUID().toString());
        }
        if (engDocument.getCreatedAt() == null) {
            engDocument.setCreatedAt(LocalDateTime.now());
        }
        if (engDocument.getUpdatedAt() == null) {
            engDocument.setUpdatedAt(LocalDateTime.now());
        }

        // 外键字段空字符串转 null，避免违反外键约束
        engDocument.setContainerOid(normalizeOid(engDocument.getContainerOid()));
        engDocument.setFolderOid(normalizeOid(engDocument.getFolderOid()));
        engDocument.setStageOid(normalizeOid(engDocument.getStageOid()));
        engDocument.setClsOid(normalizeOid(engDocument.getClsOid()));

        numberService.generateNumberIfNeeded(engDocument, engDocument.getTypeDefinitionCode());
        engDocumentMapper.insert(engDocument);

        EngineeringDocumentIteration iter = new EngineeringDocumentIteration();
        iter.setOid(UUID.randomUUID().toString());
        iter.setMasterOid(engDocument.getOid());
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setBranchId(BRANCH_MASTER);
        iter.setDeleteMark(false);
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(engDocument.getCreator());
        iter.setTenantOid(engDocument.getTenantOid());
        iter.setCkfileOid(normalizeOid(ckfileOid));
        iter.setCadName(cadName);
        iter.setCadType(cadType);
        iter.setCadTool(cadTool);

        // 大版本取自类型绑定的版本规则（与 Part / Document 一致）
        String versionRuleCode = versionRuleService.resolveVersionRuleCode(engDocument.getTypeDefinitionCode());
        if (versionRuleCode != null) {
            try {
                iter.setRevision(versionRuleService.getFirstRevision(versionRuleCode));
            } catch (Exception e) {
                log.warn("版本规则获取失败，使用默认 A: ruleCode={}, error={}", versionRuleCode, e.getMessage());
                iter.setRevision(FALLBACK_REVISION);
            }
        } else {
            iter.setRevision(FALLBACK_REVISION);
        }

        lifecycleTemplateService.initLifecycle(iter, engDocument.getTypeDefinitionCode());
        iterationMapper.insert(iter);

        return engDocument;
    }

    // ==================== MasterService 实现（版本工厂） ====================

    @Override
    public IterationEntity createInitialIteration(MasterEntity master) {
        EngineeringDocumentIteration iter = new EngineeringDocumentIteration();
        iter.setOid(UUID.randomUUID().toString());
        iter.setMasterOid(master.getOid());
        iter.setRevision(FALLBACK_REVISION);
        iter.setIteration(1);
        iter.setLatest(true);
        iter.setCheckedOut(false);
        iter.setBranchId(BRANCH_MASTER);
        iter.setCreatedAt(LocalDateTime.now());
        iter.setUpdatedAt(LocalDateTime.now());
        iter.setCreator(master.getCreator());
        iterationMapper.insert(iter);
        return iter;
    }

    @Override
    public IterationEntity createDerivedIteration(MasterEntity master, IterationEntity source) {
        EngineeringDocumentIteration src = (EngineeringDocumentIteration) source;
        EngineeringDocumentIteration derived = new EngineeringDocumentIteration();
        derived.setOid(UUID.randomUUID().toString());
        derived.setMasterOid(master.getOid());
        derived.setRevision(src.getRevision());
        derived.setIteration(src.getIteration());
        derived.setLatest(false);
        derived.setCheckedOut(false);
        derived.setDerivedFromOid(src.getOid());
        derived.setDerivedAt(LocalDateTime.now());
        derived.setBranchId(src.getBranchId() != null ? src.getBranchId() : BRANCH_MASTER);
        derived.setCreatedAt(LocalDateTime.now());
        derived.setUpdatedAt(LocalDateTime.now());
        // 继承 CAD 专有属性
        derived.setCkfileOid(src.getCkfileOid());
        derived.setCadName(src.getCadName());
        derived.setCadType(src.getCadType());
        derived.setCadTool(src.getCadTool());
        derived.setSheetSize(src.getSheetSize());
        derived.setScale(src.getScale());
        derived.setSheetNumber(src.getSheetNumber());
        derived.setSheetCount(src.getSheetCount());
        derived.setProjection(src.getProjection());
        derived.setAuthor(src.getAuthor());
        derived.setMaterial(src.getMaterial());
        derived.setWeight(src.getWeight());
        iterationMapper.insert(derived);
        return derived;
    }

    @Override
    public void updateFrom(MasterEntity target, MasterEntity source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getNumber() != null) target.setNumber(source.getNumber());
        if (source.getDescription() != null) target.setDescription(source.getDescription());
    }

    // ==================== 更新 ====================

    @Override
    @Transactional
    public EngineeringDocument update(EngineeringDocument engDocument) {
        EngineeringDocument existing = engDocumentMapper.selectByOid(engDocument.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("工程数据不存在: " + engDocument.getOid());
        }
        existing.setName(engDocument.getName());
        existing.setDescription(engDocument.getDescription());
        existing.setTypeDefinitionCode(engDocument.getTypeDefinitionCode());
        existing.setContainerOid(normalizeOid(engDocument.getContainerOid()));
        existing.setContainerType(engDocument.getContainerType());
        existing.setFolderOid(normalizeOid(engDocument.getFolderOid()));
        existing.setStageOid(normalizeOid(engDocument.getStageOid()));
        existing.setClsOid(normalizeOid(engDocument.getClsOid()));
        existing.setUpdater(engDocument.getUpdater());
        existing.setUpdatedAt(LocalDateTime.now());
        engDocumentMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public EngineeringDocument rename(String oid, String name) {
        EngineeringDocument existing = engDocumentMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("工程数据不存在: " + oid);
        }
        existing.setName(name);
        existing.setUpdatedAt(LocalDateTime.now());
        engDocumentMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public EngineeringDocument move(String oid, String containerOid, String containerType,
                                    String folderOid, String stageOid) {
        EngineeringDocument existing = engDocumentMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("工程数据不存在: " + oid);
        }
        if (containerOid != null) existing.setContainerOid(normalizeOid(containerOid));
        if (containerType != null) existing.setContainerType(containerType);
        if (folderOid != null) existing.setFolderOid(normalizeOid(folderOid));
        if (stageOid != null) existing.setStageOid(normalizeOid(stageOid));
        existing.setUpdatedAt(LocalDateTime.now());
        engDocumentMapper.update(existing);
        return existing;
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void delete(String oid) {
        EngineeringDocument existing = engDocumentMapper.selectByOid(oid);
        if (existing == null) {
            return;
        }
        for (EngineeringDocumentIteration iter : iterationMapper.selectByMasterOid(oid)) {
            iterationMapper.deleteByOid(iter.getOid());
        }
        engDocumentMapper.deleteByOid(oid);
    }

    @Override
    @Transactional
    public void deleteLatestIteration(String oid) {
        EngineeringDocumentIteration latest = iterationMapper.selectLatestByMasterOid(oid);
        if (latest == null) {
            return;
        }
        iterationMapper.deleteByOid(latest.getOid());

        List<EngineeringDocumentIteration> remaining = iterationMapper.selectByMasterOid(oid);
        if (remaining == null || remaining.isEmpty()) {
            // 无剩余版本，删除主对象
            engDocumentMapper.deleteByOid(oid);
            return;
        }
        // 将剩余中最新的版本标记为 latest=true（查询按 revision/iteration 降序，取首条）
        EngineeringDocumentIteration newLatest = remaining.get(0);
        if (!newLatest.isLatest()) {
            newLatest.setLatest(true);
            newLatest.setUpdatedAt(LocalDateTime.now());
            iterationMapper.update(newLatest);
        }
    }

    @Override
    @Transactional
    public void setLifecycleStateInstance(String oid, String entityVersion, String targetStateCode) {
        EngineeringDocument document = engDocumentMapper.selectByOid(oid);
        if (document == null) {
            throw new IllegalArgumentException("工程数据不存在: " + oid);
        }
        EngineeringDocumentIteration iter = resolveVersion(oid, entityVersion);
        lifecycleTemplateService.moveToState(iter, targetStateCode);
        iter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iter);
        log.info("设置生命周期状态: engDocumentOid={}, version={}.{}, status={}", oid,
                iter.getRevision(), iter.getIteration(),
                iter.getStatus() != null ? iter.getStatus().getCode() : null);
    }

    @Override
    @Transactional
    public void resetLifecycleStateInstance(String oid, String entityVersion) {
        EngineeringDocument document = engDocumentMapper.selectByOid(oid);
        if (document == null) {
            throw new IllegalArgumentException("工程数据不存在: " + oid);
        }
        EngineeringDocumentIteration iter = resolveVersion(oid, entityVersion);
        lifecycleTemplateService.moveToInitialState(iter);
        iter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iter);
        log.info("退回初始状态: engDocumentOid={}, version={}.{}, status={}", oid,
                iter.getRevision(), iter.getIteration(),
                iter.getStatus() != null ? iter.getStatus().getCode() : null);
    }

    /**
     * 取「该大版本当前的最新小版本」—— 流程针对的是大版本，落到具体版本时统一按这个口径解析。
     *
     * <p>{@code selectByMasterOid} 已按 revision、iteration 降序，故同大版本里第一条即最新小版本。
     */
    private EngineeringDocumentIteration resolveVersion(String masterOid, String entityVersion) {
        List<EngineeringDocumentIteration> all = iterationMapper.selectByMasterOid(masterOid);
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("工程数据没有可用版本: " + masterOid);
        }
        String want = entityVersion == null ? "" : entityVersion.trim();
        if (want.isEmpty()) {
            return all.get(0);
        }
        for (EngineeringDocumentIteration iter : all) {
            if (want.equalsIgnoreCase(iter.getRevision())) {
                return iter;
            }
        }
        throw new IllegalStateException("工程数据 " + masterOid + " 不存在大版本 " + want);
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
        EngineeringDocument document = engDocumentMapper.selectByOid(oid);
        if (document == null) {
            throw new IllegalArgumentException("工程数据不存在: " + oid);
        }
        EngineeringDocumentIteration currentIter = iterationMapper.selectLatestByMasterOid(oid);
        if (currentIter == null) {
            throw new IllegalArgumentException("工程数据没有可用版本: " + oid);
        }

        // 1. 原最新版本 latest → false
        currentIter.setLatest(false);
        iterationMapper.update(currentIter);

        // 2. 创建新的大版本（revision+1，iteration=1）
        //    注意：ck_eng_document_iteration 无 view 列（与 ck_document_iteration 不同），故不做视图继承
        EngineeringDocumentIteration copy = new EngineeringDocumentIteration();
        copy.setOid(UUID.randomUUID().toString());
        copy.setMasterOid(oid);
        copy.setIteration(1);
        copy.setLatest(true);
        copy.setCheckedOut(false);
        copy.setBranchId(currentIter.getBranchId() != null ? currentIter.getBranchId() : BRANCH_MASTER);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy.setCreator(currentIter.getCreator());
        copy.setTenantOid(currentIter.getTenantOid());
        copy.setStatus(currentIter.getStatus());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        // 继承 CAD 专有属性
        copy.setCkfileOid(currentIter.getCkfileOid());
        copy.setCadName(currentIter.getCadName());
        copy.setCadType(currentIter.getCadType());
        copy.setCadTool(currentIter.getCadTool());
        copy.setSheetSize(currentIter.getSheetSize());
        copy.setScale(currentIter.getScale());
        copy.setSheetNumber(currentIter.getSheetNumber());
        copy.setSheetCount(currentIter.getSheetCount());
        copy.setProjection(currentIter.getProjection());
        copy.setAuthor(currentIter.getAuthor());
        copy.setMaterial(currentIter.getMaterial());
        copy.setWeight(currentIter.getWeight());

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

        log.info("新建视图版本成功: engDocOid={}, {}.{} -> {}.1", oid,
                currentIter.getRevision(), currentIter.getIteration(), copy.getRevision());
    }

    @Override
    public List<EngineeringDocumentVO> findVOsByFolder(String folderOid) {
        List<EngineeringDocument> docs = engDocumentMapper.selectByFolderOid(folderOid);
        List<EngineeringDocumentVO> vos = new java.util.ArrayList<>();
        for (EngineeringDocument doc : docs) {
            EngineeringDocumentVO vo = new EngineeringDocumentVO();
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

            TypeDefinition td = typeDefinitionService.findByCode(doc.getTypeDefinitionCode());
            if (td != null) {
                vo.setTypeDefinitionName(td.getName());
            }

            EngineeringDocumentIteration latestIter = iterationMapper.selectLatestByMasterOid(doc.getOid());
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
                vo.setCadName(latestIter.getCadName());
                vo.setCadType(latestIter.getCadType());
                vo.setCadTool(latestIter.getCadTool());
                if (latestIter.getStatus() != null) {
                    vo.setStatusCode(latestIter.getStatus().getCode());
                    // 显示名要走解析器：迭代上的 status 只带 code，界面显示用"草稿/已发布"
                    vo.setStatusName(lifecycleStatusService.displayName(
                            latestIter.getLifecycleTemplateIterationOid(), latestIter.getStatus().getCode()));
                }
            }
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 查询 ====================

    @Override
    public EngineeringDocument findByOid(String oid) {
        return oid != null ? engDocumentMapper.selectByOid(oid) : null;
    }

    @Override
    public List<EngineeringDocument> findByContainerOid(String containerOid) {
        return engDocumentMapper.selectByContainerOid(containerOid);
    }

    @Override
    public List<EngineeringDocument> findByContainerAndStage(String containerOid, String stageOid) {
        return engDocumentMapper.selectByContainerAndStage(containerOid, stageOid);
    }

    @Override
    public List<EngineeringDocument> findByFolder(String folderOid) {
        return engDocumentMapper.selectByFolderOid(folderOid);
    }

    @Override
    public List<EngineeringDocument> findByTypeDefinitionCode(String typeDefinitionCode) {
        return engDocumentMapper.selectByTypeDefinitionCode(typeDefinitionCode);
    }

    @Override
    public EngineeringDocumentIteration findLatestIteration(String masterOid) {
        return iterationMapper.selectLatestByMasterOid(masterOid);
    }

    @Override
    @Transactional
    public void updateLatestIterationAttributes(String masterOid, EngineeringDocumentIteration attributes) {
        if (attributes == null) return;
        EngineeringDocumentIteration latest = iterationMapper.selectLatestByMasterOid(masterOid);
        if (latest == null) return;

        if (attributes.getCadName() != null) latest.setCadName(attributes.getCadName());
        if (attributes.getCadType() != null) latest.setCadType(attributes.getCadType());
        if (attributes.getCadTool() != null) latest.setCadTool(attributes.getCadTool());
        if (attributes.getSheetSize() != null) latest.setSheetSize(attributes.getSheetSize());
        if (attributes.getScale() != null) latest.setScale(attributes.getScale());
        if (attributes.getSheetNumber() != null) latest.setSheetNumber(attributes.getSheetNumber());
        if (attributes.getSheetCount() != null) latest.setSheetCount(attributes.getSheetCount());
        if (attributes.getProjection() != null) latest.setProjection(attributes.getProjection());
        if (attributes.getAuthor() != null) latest.setAuthor(attributes.getAuthor());
        if (attributes.getMaterial() != null) latest.setMaterial(attributes.getMaterial());
        if (attributes.getWeight() != null) latest.setWeight(attributes.getWeight());
        if (attributes.getCkfileOid() != null) latest.setCkfileOid(attributes.getCkfileOid());

        latest.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(latest);
    }

    @Override
    public EngineeringDocumentIteration findIterationByOid(String iterationOid) {
        return iterationOid != null ? iterationMapper.selectByOid(iterationOid) : null;
    }

    @Override
    public List<EngineeringDocumentIteration> findIterationsByMaster(String masterOid) {
        return iterationMapper.selectByMasterOid(masterOid);
    }

    // ==================== 私有工具 ====================

    /** 空字符串外键 oid 转 null，避免违反外键约束 */
    private String normalizeOid(String oid) {
        if (oid == null) return null;
        String trimmed = oid.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
