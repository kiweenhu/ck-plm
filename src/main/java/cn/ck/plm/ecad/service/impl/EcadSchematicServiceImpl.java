/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.service.impl;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.base.service.api.CKFileService;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.ecad.dto.BomSyncItem;
import cn.ck.plm.ecad.dto.BomSyncResult;
import cn.ck.plm.ecad.entity.EcadBomSyncLog;
import cn.ck.plm.ecad.entity.EcadDesignInstance;
import cn.ck.plm.ecad.entity.EcadSchematic;
import cn.ck.plm.ecad.entity.EcadSchematicIteration;
import cn.ck.plm.ecad.mapper.EcadBomSyncLogMapper;
import cn.ck.plm.ecad.mapper.EcadDesignInstanceMapper;
import cn.ck.plm.ecad.mapper.EcadSchematicIterationMapper;
import cn.ck.plm.ecad.mapper.EcadSchematicMapper;
import cn.ck.plm.ecad.service.api.EcadSchematicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link EcadSchematicService} 实现。
 *
 * <h3>版本流转</h3>
 * <pre>
 * 检出：latest 迭代 → latest=false，派生新迭代（iteration+1, checkedOut=true, derivedFromOid 指源）
 * 检入：检出的工作副本 → checkedOut=false，文件随迭代生效
 * 取消检出：删除派生副本，恢复源版本 latest=true
 * 新大版本：revision+1，iteration=1（v1 实现为字母递增，未接 VersionRuleService）
 * </pre>
 */
@Service
public class EcadSchematicServiceImpl implements EcadSchematicService {

    private static final Logger log = LoggerFactory.getLogger(EcadSchematicServiceImpl.class);

    /** 默认 EDA 工具 */
    private static final String DEFAULT_EDA_TOOL = "Cadence OrCAD Capture";

    /** 编号规则（未配置时回退 SCH- 前缀） */
    private static final String NUMBER_RULE = "ECAD_SCHEMATIC-NUM";

    private final EcadSchematicMapper schematicMapper;
    private final EcadSchematicIterationMapper iterationMapper;
    private final EcadDesignInstanceMapper instanceMapper;
    private final EcadBomSyncLogMapper syncLogMapper;
    private final NumberService numberService;
    private final FileStorageService fileStorage;
    private final CKFileService ckFileService;
    private final ObjectMapper objectMapper;

    public EcadSchematicServiceImpl(EcadSchematicMapper schematicMapper,
                                    EcadSchematicIterationMapper iterationMapper,
                                    EcadDesignInstanceMapper instanceMapper,
                                    EcadBomSyncLogMapper syncLogMapper,
                                    NumberService numberService,
                                    FileStorageService fileStorage,
                                    CKFileService ckFileService,
                                    ObjectMapper objectMapper) {
        this.schematicMapper = schematicMapper;
        this.iterationMapper = iterationMapper;
        this.instanceMapper = instanceMapper;
        this.syncLogMapper = syncLogMapper;
        this.numberService = numberService;
        this.fileStorage = fileStorage;
        this.ckFileService = ckFileService;
        this.objectMapper = objectMapper;
    }

    // ==================== 主数据 ====================

    @Override
    @Transactional
    public EcadSchematic create(EcadSchematic schematic, String user) {
        if (schematic.getNumber() == null || schematic.getNumber().isBlank()) {
            schematic.setNumber(generateNumber());
        }
        if (schematic.getEdaTool() == null || schematic.getEdaTool().isBlank()) {
            schematic.setEdaTool(DEFAULT_EDA_TOOL);
        }
        if (schematic.getFileFormat() == null || schematic.getFileFormat().isBlank()) {
            schematic.setFileFormat("DSN");
        }
        LocalDateTime now = LocalDateTime.now();
        schematic.setOid(UUID.randomUUID().toString());
        schematic.setCreator(user);
        schematic.setCreatedAt(now);
        schematic.setUpdater(user);
        schematic.setUpdatedAt(now);
        if (schematic.getTenantOid() == null) {
            schematic.setTenantOid(TenantContext.get());
        }
        schematicMapper.insert(schematic);

        // 首次迭代 A.1
        EcadSchematicIteration first = new EcadSchematicIteration();
        first.setOid(UUID.randomUUID().toString());
        first.setMasterOid(schematic.getOid());
        first.setRevision("A");
        first.setIteration(1);
        first.setLatest(true);
        first.setTenantOid(schematic.getTenantOid());
        first.setCreator(user);
        first.setCreatedAt(now);
        first.setUpdater(user);
        first.setUpdatedAt(now);
        iterationMapper.insert(first);

        schematic.setLatestIterationOid(first.getOid());
        schematicMapper.update(schematic);
        return schematic;
    }

    @Override
    public EcadSchematic findByOid(String oid) {
        EcadSchematic schematic = schematicMapper.selectByOid(oid);
        if (schematic == null) {
            throw new IllegalArgumentException("原理图不存在: " + oid);
        }
        return schematic;
    }

    @Override
    public EcadSchematic findByNumber(String number) {
        return schematicMapper.selectByNumber(number);
    }

    @Override
    public List<EcadSchematic> listByProjectOid(String projectOid) {
        return schematicMapper.selectByProjectOid(projectOid);
    }

    @Override
    @Transactional
    public EcadSchematic update(EcadSchematic schematic, String user) {
        EcadSchematic existing = findByOid(schematic.getOid());
        if (schematic.getName() != null) existing.setName(schematic.getName());
        if (schematic.getDescription() != null) existing.setDescription(schematic.getDescription());
        if (schematic.getProjectOid() != null) existing.setProjectOid(schematic.getProjectOid());
        existing.setUpdater(user);
        existing.setUpdatedAt(LocalDateTime.now());
        schematicMapper.update(existing);
        return existing;
    }

    // ==================== 版本控制 ====================

    @Override
    @Transactional
    public EcadSchematicIteration checkout(String masterOid, String comment, String user) {
        findByOid(masterOid);
        EcadSchematicIteration latest = requireLatest(masterOid);
        if (latest.isCheckedOut()) {
            throw new IllegalStateException("原理图已被 " + latest.getCheckedOutBy()
                    + " 检出，检出注释: "
                    + (latest.getCheckedOutComment() != null ? latest.getCheckedOutComment() : "无"));
        }

        // 1. 源版本 latest → false
        latest.setLatest(false);
        iterationMapper.update(latest);

        // 2. 派生同大版本的新小版本
        EcadSchematicIteration copy = new EcadSchematicIteration();
        LocalDateTime now = LocalDateTime.now();
        copy.setOid(UUID.randomUUID().toString());
        copy.setMasterOid(latest.getMasterOid());
        copy.setRevision(latest.getRevision());
        copy.setIteration(latest.getIteration() + 1);
        copy.setLatest(true);
        copy.setCheckedOut(true);
        copy.setCheckedOutBy(user);
        copy.setCheckedOutComment(comment);
        copy.setDerivedFromOid(latest.getOid());
        copy.setDerivedAt(now);
        copy.setCkfileOid(latest.getCkfileOid());
        copy.setSheetCount(latest.getSheetCount());
        copy.setComponentCount(latest.getComponentCount());
        copy.setTenantOid(latest.getTenantOid());
        copy.setCreator(user);
        copy.setCreatedAt(now);
        copy.setUpdater(user);
        copy.setUpdatedAt(now);
        iterationMapper.insert(copy);

        touchMasterLatest(masterOid, copy.getOid(), user);
        log.info("原理图检出成功: masterOid={}, {}.{} -> {}.{}, user={}", masterOid,
                latest.getRevision(), latest.getIteration(), copy.getRevision(), copy.getIteration(), user);
        return copy;
    }

    @Override
    @Transactional
    public EcadSchematicIteration checkin(String masterOid, String user) {
        findByOid(masterOid);
        EcadSchematicIteration checkedOut = requireCheckedOut(masterOid, user);

        checkedOut.setCheckedOut(false);
        checkedOut.setCheckedOutBy(null);
        checkedOut.setCheckedOutComment(null);
        checkedOut.setUpdatedAt(LocalDateTime.now());
        checkedOut.setUpdater(user);
        iterationMapper.update(checkedOut);

        log.info("原理图检入成功: masterOid={}, version={}.{}, user={}", masterOid,
                checkedOut.getRevision(), checkedOut.getIteration(), user);
        return checkedOut;
    }

    @Override
    @Transactional
    public void undoCheckout(String masterOid, String user) {
        findByOid(masterOid);
        EcadSchematicIteration checkedOut = requireCheckedOut(masterOid, user);

        EcadSchematicIteration source = checkedOut.getDerivedFromOid() != null
                ? iterationMapper.selectByOid(checkedOut.getDerivedFromOid()) : null;

        // 1. 删除派生副本
        iterationMapper.deleteByOid(checkedOut.getOid());

        // 2. 恢复源版本
        if (source != null) {
            source.setLatest(true);
            iterationMapper.update(source);
            touchMasterLatest(masterOid, source.getOid(), user);
        }
        log.info("原理图取消检出成功: masterOid={}, user={}", masterOid, user);
    }

    @Override
    @Transactional
    public EcadSchematicIteration newRevision(String masterOid, String user) {
        findByOid(masterOid);
        EcadSchematicIteration latest = requireLatest(masterOid);
        if (latest.isCheckedOut()) {
            throw new IllegalStateException("存在未检入的工作副本，请先检入再新建大版本");
        }

        EcadSchematicIteration next = new EcadSchematicIteration();
        LocalDateTime now = LocalDateTime.now();
        next.setOid(UUID.randomUUID().toString());
        next.setMasterOid(latest.getMasterOid());
        next.setRevision(nextRevisionChar(latest.getRevision()));
        next.setIteration(1);
        next.setLatest(true);
        next.setDerivedFromOid(latest.getOid());
        next.setDerivedAt(now);
        next.setCkfileOid(latest.getCkfileOid());
        next.setSheetCount(latest.getSheetCount());
        next.setComponentCount(latest.getComponentCount());
        next.setTenantOid(latest.getTenantOid());
        next.setCreator(user);
        next.setCreatedAt(now);
        next.setUpdater(user);
        next.setUpdatedAt(now);

        latest.setLatest(false);
        iterationMapper.update(latest);
        iterationMapper.insert(next);
        touchMasterLatest(masterOid, next.getOid(), user);

        log.info("原理图新建大版本: masterOid={}, {} -> {}, user={}", masterOid,
                latest.getRevision(), next.getRevision(), user);
        return next;
    }

    @Override
    public List<EcadSchematicIteration> listIterations(String masterOid) {
        findByOid(masterOid);
        return iterationMapper.selectByMasterOid(masterOid);
    }

    @Override
    public EcadSchematicIteration latestIteration(String masterOid) {
        return requireLatest(masterOid);
    }

    // ==================== 文件 ====================

    @Override
    @Transactional
    public EcadSchematicIteration uploadDsn(String masterOid, MultipartFile file,
                                            Integer sheetCount, Integer componentCount,
                                            String user) throws IOException {
        findByOid(masterOid);
        EcadSchematicIteration iteration = requireCheckedOut(masterOid, user);

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "design.dsn";
        FileStorageService.StoredFile stored = fileStorage.store(
                file.getInputStream(), originalName, FileStorageService.FileCategory.CAD,
                TenantContext.get());

        CKFile ckFile = new CKFile();
        ckFile.setOid(UUID.randomUUID().toString());
        ckFile.setSourceType("LOCAL");
        ckFile.setSourceUrl(stored.getStoragePath());
        ckFile.setFileName(originalName);
        ckFile.setFileSize(stored.getFileSize());
        ckFile.setStoragePath(stored.getStoragePath());
        ckFile.setMimeType("application/octet-stream");
        ckFile.setTenantOid(TenantContext.get());
        ckFile.setCreator(user);
        ckFile.setUpdater(user);
        ckFileService.create(ckFile);

        iteration.setCkfileOid(ckFile.getOid());
        if (sheetCount != null) iteration.setSheetCount(sheetCount);
        if (componentCount != null) iteration.setComponentCount(componentCount);
        iteration.setUpdater(user);
        iteration.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(iteration);
        return iteration;
    }

    // ==================== BOM 同步 ====================

    @Override
    @Transactional
    public BomSyncResult syncBom(String masterOid, List<BomSyncItem> items, String user) {
        findByOid(masterOid);
        EcadSchematicIteration latest = requireLatest(masterOid);

        // 1. 上次状态（按位号索引）
        Map<String, BomSyncItem> previous = new LinkedHashMap<>();
        for (EcadDesignInstance inst : instanceMapper.selectByDesign(masterOid, "SCHEMATIC")) {
            BomSyncItem item = new BomSyncItem();
            item.setReference(inst.getReference());
            item.setQuantity(inst.getQuantity());
            item.setValue(inst.getValue());
            item.setPartNumber(inst.getPartNumber());
            item.setFootprint(inst.getFootprintName());
            previous.put(inst.getReference(), item);
        }

        // 2. 本次状态（部件匹配）
        List<BomSyncItem> unmatched = new ArrayList<>();
        Map<String, BomSyncItem> current = new LinkedHashMap<>();
        Map<String, String> matchedPart = new LinkedHashMap<>();
        for (BomSyncItem item : items) {
            if (item.getReference() == null || item.getReference().isBlank()) {
                continue;
            }
            current.put(item.getReference(), item);
            String partOid = matchPart(item);
            matchedPart.put(item.getReference(), partOid);
            if (partOid == null) {
                unmatched.add(item);
            }
        }

        // 3. 差异
        List<BomSyncItem> added = new ArrayList<>();
        List<BomSyncItem> removed = new ArrayList<>();
        List<BomSyncItem> changed = new ArrayList<>();
        for (Map.Entry<String, BomSyncItem> e : current.entrySet()) {
            BomSyncItem prev = previous.get(e.getKey());
            if (prev == null) {
                added.add(e.getValue());
            } else if (!sameLine(prev, e.getValue())) {
                changed.add(e.getValue());
            }
        }
        for (Map.Entry<String, BomSyncItem> e : previous.entrySet()) {
            if (!current.containsKey(e.getKey())) {
                removed.add(e.getValue());
            }
        }

        // 4. 落地实例（整组替换）
        instanceMapper.deleteByDesign(masterOid, "SCHEMATIC");
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, BomSyncItem> e : current.entrySet()) {
            BomSyncItem item = e.getValue();
            EcadDesignInstance inst = new EcadDesignInstance();
            inst.setOid(UUID.randomUUID().toString());
            inst.setDesignOid(masterOid);
            inst.setDesignType("SCHEMATIC");
            inst.setReference(item.getReference());
            inst.setComponentOid(matchedPart.get(item.getReference()));
            inst.setQuantity(item.getQuantity() != null ? item.getQuantity() : 1);
            inst.setValue(item.getValue());
            inst.setFootprintName(item.getFootprint());
            inst.setPartNumber(item.getPartNumber());
            inst.setTenantOid(TenantContext.get());
            inst.setCreator(user);
            inst.setCreatedAt(now);
            inst.setUpdater(user);
            inst.setUpdatedAt(now);
            instanceMapper.insert(inst);
        }

        // 5. 快照与日志
        BomSyncResult result = new BomSyncResult();
        result.setTotalCount(current.size());
        result.setAdded(added);
        result.setRemoved(removed);
        result.setChanged(changed);
        result.setUnmatched(unmatched);

        latest.setComponentCount(current.size());
        latest.setBomJson(toJson(items));
        latest.setUpdater(user);
        latest.setUpdatedAt(now);
        iterationMapper.update(latest);

        EcadBomSyncLog syncLog = new EcadBomSyncLog();
        syncLog.setOid(UUID.randomUUID().toString());
        syncLog.setSchematicOid(masterOid);
        syncLog.setIterationOid(latest.getOid());
        syncLog.setAddedCount(added.size());
        syncLog.setRemovedCount(removed.size());
        syncLog.setChangedCount(changed.size());
        syncLog.setDiffJson(toJson(result));
        syncLog.setTenantOid(TenantContext.get());
        syncLog.setCreator(user);
        syncLog.setCreatedAt(now);
        syncLog.setUpdater(user);
        syncLog.setUpdatedAt(now);
        syncLogMapper.insert(syncLog);

        log.info("原理图 BOM 同步完成: masterOid={}, total={}, +{} -{} ~{}, 未匹配={}",
                masterOid, current.size(), added.size(), removed.size(), changed.size(), unmatched.size());
        return result;
    }

    @Override
    public List<EcadDesignInstance> listInstances(String masterOid) {
        findByOid(masterOid);
        return instanceMapper.selectByDesign(masterOid, "SCHEMATIC");
    }

    // ==================== 内部方法 ====================

    private EcadSchematicIteration requireLatest(String masterOid) {
        EcadSchematicIteration latest = iterationMapper.selectLatestByMasterOid(masterOid);
        if (latest == null) {
            throw new IllegalArgumentException("原理图没有可用版本: " + masterOid);
        }
        return latest;
    }

    private EcadSchematicIteration requireCheckedOut(String masterOid, String user) {
        EcadSchematicIteration checkedOut = null;
        for (EcadSchematicIteration iter : iterationMapper.selectByMasterOid(masterOid)) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOut = iter;
            }
        }
        if (checkedOut == null) {
            throw new IllegalStateException("该原理图未被检出: " + masterOid);
        }
        if (!user.equals(checkedOut.getCheckedOutBy())) {
            throw new IllegalStateException("只有检出人 " + checkedOut.getCheckedOutBy() + " 才能执行此操作");
        }
        return checkedOut;
    }

    private void touchMasterLatest(String masterOid, String iterationOid, String user) {
        EcadSchematic schematic = schematicMapper.selectByOid(masterOid);
        if (schematic != null) {
            schematic.setLatestIterationOid(iterationOid);
            schematic.setUpdater(user);
            schematic.setUpdatedAt(LocalDateTime.now());
            schematicMapper.update(schematic);
        }
    }

    private String nextRevisionChar(String revision) {
        if (revision == null || revision.isBlank()) {
            return "A";
        }
        char c = revision.charAt(revision.length() - 1);
        if (c >= 'Z') {
            throw new IllegalStateException("大版本号已用尽（" + revision + "），请接入版本规则后扩展");
        }
        return revision.substring(0, revision.length() - 1) + (char) (c + 1);
    }

    private String generateNumber() {
        try {
            String number = numberService.generate(NUMBER_RULE);
            if (number != null && !number.isBlank()) {
                return number;
            }
        } catch (Exception e) {
            log.debug("编号规则 {} 未配置，回退默认编号: {}", NUMBER_RULE, e.getMessage());
        }
        return "SCH-" + System.currentTimeMillis();
    }

    /** 按编号精确匹配部件；无编号时按 value 精确匹配（value 常被填成 PLM 编号） */
    private String matchPart(BomSyncItem item) {
        if (item.getPartNumber() != null && !item.getPartNumber().isBlank()) {
            return instanceMapper.selectPartOidByNumber(item.getPartNumber().trim());
        }
        if (item.getValue() != null && !item.getValue().isBlank()) {
            return instanceMapper.selectPartOidByNumber(item.getValue().trim());
        }
        return null;
    }

    private boolean sameLine(BomSyncItem a, BomSyncItem b) {
        return eq(a.getQuantity(), b.getQuantity())
                && eq(normalize(a.getValue()), normalize(b.getValue()))
                && eq(normalize(a.getPartNumber()), normalize(b.getPartNumber()))
                && eq(normalize(a.getFootprint()), normalize(b.getFootprint()));
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
