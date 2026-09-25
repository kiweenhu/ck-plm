package cn.ck.plm.part.service.impl;

import cn.ck.plm.part.dto.PartVO;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartMapper;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.service.api.PartService;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.cls.service.impl.ClsIbaDataSupport;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.base.service.api.ViewService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PartServiceImpl implements PartService, SoftTypeInstanceCapability {

    private static final Logger log = LoggerFactory.getLogger(PartServiceImpl.class);

    /** 企业级资源库容器类型（电子元器件等企业资源的归属容器） */
    private static final String RESOURCE_CONTAINER_TYPE = "CORP_RESOURCE";

    /** 能力宿主 code（= type_definition.root_type_code） */
    private static final String HOST = "PART";

    /** 实体 IBA 属性归属的实体类型编码（ck_type_iba_data.entity_type） */
    private static final String IBA_ENTITY_TYPE = "PART";

    private final PartMapper partMapper;
    private final PartIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final NumberService numberService;
    private final VersionRuleService versionRuleService;
    private final LifecycleTemplateService lifecycleTemplateService;
    /** 状态 code → 显示名（列表/详情都要给"人看得懂"的那个字，见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;
    private final ViewService viewService;
    private final IbaDataSupport ibaDataSupport;
    private final ClsIbaDataSupport clsIbaDataSupport;
    private final ClsIbaDataService clsIbaDataService;
    private final ObjectMapper objectMapper;

    public PartServiceImpl(PartMapper partMapper,
                            PartIterationMapper iterationMapper,
                            TypeDefinitionMapper typeDefinitionMapper,
                            NumberService numberService,
                            VersionRuleService versionRuleService,
                            LifecycleTemplateService lifecycleTemplateService,
                            LifecycleStatusService lifecycleStatusService,
                            ViewService viewService,
                            IbaDataSupport ibaDataSupport,
                            ClsIbaDataSupport clsIbaDataSupport,
                            ClsIbaDataService clsIbaDataService,
                            ObjectMapper objectMapper) {
        this.partMapper = partMapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.numberService = numberService;
        this.versionRuleService = versionRuleService;
        this.lifecycleTemplateService = lifecycleTemplateService;
        this.lifecycleStatusService = lifecycleStatusService;
        this.viewService = viewService;
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
        Part part = objectMapper.convertValue(payload, Part.class);
        // 以软类型编码落库，保证软类型身份不被宿主根类型覆盖
        part.setTypeDefinitionCode(type.getCode());

        String ckfileOid = ibaDataSupport.getString(payload, "ckfileOid");
        String attachmentOid = ibaDataSupport.getString(payload, "attachmentOid");
        String unit = ibaDataSupport.getString(payload, "unit");
        String source = ibaDataSupport.getString(payload, "source");

        Part created = create(part, ckfileOid, attachmentOid, unit, source);

        PartIteration latest = findLatestIteration(created.getOid());
        String iterationOid = latest != null ? latest.getOid() : created.getOid();
        // 分类 IBA（迭代级）：entity_oid = 最新迭代 oid
        clsIbaDataSupport.saveClsIbaValues(iterationOid, created.getClsOid(), payload);
        // 实体 IBA（实体级）
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
        Part part = findByOid(oid);
        if (part == null) {
            return null;
        }
        Map<String, Object> result = objectMapper.convertValue(part,
                new TypeReference<Map<String, Object>>() {});
        // 指定 iterationOid 时返回该迭代，否则返回最新迭代
        String iterationOid = ibaDataSupport.getString(params, "iterationOid");
        PartIteration iteration = iterationOid != null
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
            result.put("unit", iteration.getUnit());
            result.put("source", iteration.getSource());
            result.put("view", iteration.getView() != null ? iteration.getView().getCode() : null);
            if (iteration.getStatus() != null) {
                result.put("statusCode", iteration.getStatus().getCode());
                // 显示名要走解析器：迭代上的 status 只带 code（见 LifecycleStatusTypeHandler），
                // 直接取 getDisplayName() 永远是 null，界面就只好退化成显示 code
                result.put("statusName", lifecycleStatusService.displayName(
                        iteration.getLifecycleTemplateIterationOid(), iteration.getStatus().getCode()));
            }
            // 附加该迭代的分类 IBA 属性值
            if (part.getClsOid() != null) {
                Map<String, Object> clsIba = clsIbaDataService.getValues(iteration.getOid(), part.getClsOid());
                if (clsIba != null) {
                    result.put("clsIba", clsIba);
                }
            }
        }
        return result;
    }

    @Override
    public Object updateInstance(String oid, Map<String, Object> body) {
        Part part = objectMapper.convertValue(body, Part.class);
        part.setOid(oid);
        Part updated = update(part);
        // 更新最新迭代的 unit/source（迭代级字段）
        updateLatestIterationAttributes(oid,
                ibaDataSupport.getString(body, "unit"),
                ibaDataSupport.getString(body, "source"));
        // 分类 IBA（迭代级，entity_oid = 最新迭代 oid）
        PartIteration latest = findLatestIteration(oid);
        String iterOid = latest != null ? latest.getOid() : oid;
        clsIbaDataSupport.saveClsIbaValues(iterOid, updated.getClsOid(), body);
        // 实体 IBA（合并保存，保留未提交字段）
        ibaDataSupport.mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
        return updated;
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

        // 电子元器件（ELECTRONIC 软类型）必须归档在企业资源库：
        // 产品线/型号下禁止创建，只能通过企业资源库-元器件库申请
        if ("ELECTRONIC".equalsIgnoreCase(part.getTypeDefinitionCode())
                && !RESOURCE_CONTAINER_TYPE.equalsIgnoreCase(part.getContainerType())) {
            throw new IllegalArgumentException(
                    "电子元器件必须归档在企业资源库的元器件库中，请通过「企业资源 → 元器件库 → 元器件申请」创建");
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
    public Part move(String oid, String containerOid, String containerType, String folderOid, String stageOid,
                     String clsOid) {
        Part existing = partMapper.selectByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("部件不存在: " + oid);
        }
        existing.setContainerOid(normalizeOid(containerOid));
        existing.setContainerType(containerType);
        existing.setFolderOid(normalizeOid(folderOid));
        existing.setStageOid(normalizeOid(stageOid));
        // 分类节点（元器件库/标准件库/通用件库按分类归档）：null = 本次不涉及分类，保持原值
        if (clsOid != null) {
            existing.setClsOid(normalizeOid(clsOid));
        }
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
    public void setLifecycleStateInstance(String oid, String entityVersion, String targetStateCode) {
        Part part = partMapper.selectByOid(oid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + oid);
        }
        PartIteration iter = resolveVersion(oid, entityVersion);
        // 迁移规则由模板决定（当前状态能不能到目标状态），不在这里自己判断
        lifecycleTemplateService.moveToState(iter, targetStateCode);
        iter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iter);
        log.info("设置生命周期状态: partOid={}, version={}.{}, status={}", oid,
                iter.getRevision(), iter.getIteration(),
                iter.getStatus() != null ? iter.getStatus().getCode() : null);
    }

    @Override
    @Transactional
    public void resetLifecycleStateInstance(String oid, String entityVersion) {
        Part part = partMapper.selectByOid(oid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + oid);
        }
        PartIteration iter = resolveVersion(oid, entityVersion);
        // 沿模板的回退规则逐跳退回初始状态（多跳在模板里没有直接规则，不允许一步跳过去）
        lifecycleTemplateService.moveToInitialState(iter);
        iter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iter);
        log.info("退回初始状态: partOid={}, version={}.{}, status={}", oid,
                iter.getRevision(), iter.getIteration(),
                iter.getStatus() != null ? iter.getStatus().getCode() : null);
    }

    /**
     * 取「该大版本当前的最新小版本」—— 流程针对的是大版本，落到具体版本时统一按这个口径解析
     * （见 ProcessEntitySet 的说明）。
     *
     * <p>{@code selectByMasterOid} 已按 revision、iteration 降序，故同大版本里第一条即最新小版本。
     *
     * @param entityVersion 大版本（如 A）；为空表示整个对象的最新版本
     */
    private PartIteration resolveVersion(String masterOid, String entityVersion) {
        List<PartIteration> all = iterationMapper.selectByMasterOid(masterOid);
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("部件没有可用版本: " + masterOid);
        }
        String want = entityVersion == null ? "" : entityVersion.trim();
        if (want.isEmpty()) {
            return all.get(0);
        }
        for (PartIteration iter : all) {
            if (want.equalsIgnoreCase(iter.getRevision())) {
                return iter;
            }
        }
        throw new IllegalStateException("部件 " + masterOid + " 不存在大版本 " + want);
    }

    /**
     * 新建视图版本（revision+1，iteration=1）—— 统一入口形态。
     *
     * <p>直接转调本宿主已有的 {@link #newViewVersion(String)}：流程里的
     * 「自动服务 → object.promote」走的正是这条路，不另写一份实现（两份必然分叉）。
     */
    @Override
    @Transactional
    public void newViewVersionInstance(String oid) {
        newViewVersion(oid);
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
                    // 旁边那个 code 只是标识，界面显示用显示名（草稿/工作中）——
                    // 这里曾经只填 code，阶段/分类清单于是直接显示 IN_WORK
                    vo.setStatusName(lifecycleStatusService.displayName(
                            latestIter.getLifecycleTemplateIterationOid(), latestIter.getStatus().getCode()));
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
                    // 旁边那个 code 只是标识，界面显示用显示名（草稿/工作中）——
                    // 这里曾经只填 code，阶段/分类清单于是直接显示 IN_WORK
                    vo.setStatusName(lifecycleStatusService.displayName(
                            latestIter.getLifecycleTemplateIterationOid(), latestIter.getStatus().getCode()));
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
