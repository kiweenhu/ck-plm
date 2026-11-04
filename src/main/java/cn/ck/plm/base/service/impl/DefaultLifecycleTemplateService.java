/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.*;
import cn.ck.plm.base.mapper.LifecycleStatusMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateIterationMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateProcessService;
import cn.ck.plm.softtype.service.api.TypeLifecycleTemplateLinkService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * {@link LifecycleTemplateService} 的数据库实现，继承版本控制能力 + CRUD 持久化。
 */
@Service
public class DefaultLifecycleTemplateService extends MasterServiceImpl implements LifecycleTemplateService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLifecycleTemplateService.class);

    private final LifecycleTemplateMapper mapper;
    private final LifecycleTemplateIterationMapper iterationMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final TypeLifecycleTemplateLinkService typeLifecycleTemplateLinkService;
    private final LifecycleStatusMapper lifecycleStatusMapper;
    /** 「类型 · 状态 → 流程模板」配置（编辑模板生成新子版本时要继承过去） */
    private final TypeLifecycleStateProcessService typeLifecycleStateProcessService;

    public DefaultLifecycleTemplateService(LifecycleTemplateMapper mapper,
                                           LifecycleTemplateIterationMapper iterationMapper,
                                           TypeDefinitionMapper typeDefinitionMapper,
                                           TypeLifecycleTemplateLinkService typeLifecycleTemplateLinkService,
                                           LifecycleStatusMapper lifecycleStatusMapper,
                                           TypeLifecycleStateProcessService typeLifecycleStateProcessService) {
        this.mapper = mapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.typeLifecycleTemplateLinkService = typeLifecycleTemplateLinkService;
        this.lifecycleStatusMapper = lifecycleStatusMapper;
        this.typeLifecycleStateProcessService = typeLifecycleStateProcessService;
    }

    @Override
    protected IterationEntity newIterationInstance() {
        return new LifecycleTemplateIteration();
    }

    // ==================== CRUD ====================

    @Override
    @Transactional
    public LifecycleTemplateMaster create(LifecycleTemplateMaster template) {
        if (template == null || template.getCode() == null || template.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("模板编码不能为空");
        }
        String code = template.getCode().trim();
        if (mapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("模板编码 '" + code + "' 已存在");
        }
        // 按租户隔离（2026-10-11）：归属必须显式落本租户 —— insert 列含 tenant_oid，不设会写 NULL；
        // 隔离后 NULL 行本租户读不到，等于"创建即失踪"。
        if (template.getTenantOid() == null) {
            template.setTenantOid(TenantContext.get());
        }
        // 1. 插入主表
        mapper.insert(template);

        // 2. 创建初始子版本 A.1
        LifecycleTemplateIteration iter = (LifecycleTemplateIteration) createInitialIteration(template);
        iterationMapper.insert(iter);

        // 3. 保存状态和流转规则（关联到子版本 oid）
        saveChildren(template, iter.getOid());

        template.setLatestIteration(iter);
        log.info("生命周期模板已创建: code={}", code);
        return template;
    }

    @Override
    @Transactional
    public LifecycleTemplateMaster update(LifecycleTemplateMaster template) {
        if (template == null || template.getCode() == null || template.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("模板编码不能为空");
        }
        String code = template.getCode().trim();
        LifecycleTemplateMaster existing = mapper.selectByCode(code);
        if (existing == null) {
            throw new IllegalArgumentException("模板编码 '" + code + "' 不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "生命周期模板");

        // 更新主表
        updateFrom(existing, template);
        existing.setActive(template.isActive());
        existing.setInitialStateCode(template.getInitialStateCode());
        existing.setUpdatedAt(LocalDateTime.now());
        mapper.update(existing);

        // 全量替换子数据：先删旧关联，创建新子版本，再保存新关联
        LifecycleTemplateIteration oldLatest = iterationMapper.selectLatestByMasterOid(existing.getOid());
        if (oldLatest != null) {
            mapper.deleteStateRefsByIterationOid(oldLatest.getOid());
            mapper.deleteTransitionRefsByIterationOid(oldLatest.getOid());

            // 标记旧 latest=false，创建新迭代号
            oldLatest.setLatest(false);
            oldLatest.setUpdatedAt(LocalDateTime.now());
            iterationMapper.update(oldLatest);

            LifecycleTemplateIteration newIter = (LifecycleTemplateIteration) createInitialIteration(existing);
            newIter.setRevision(oldLatest.getRevision());
            newIter.setIteration(oldLatest.getIteration() + 1);
            iterationMapper.insert(newIter);

            // 保存状态和流转规则到新子版本
            template.setOid(existing.getOid());
            saveChildren(template, newIter.getOid());
            // 「可修订」是权限开关而非结构：编辑模板时改了它，同样要落到全部子版本 ——
            // 否则在途对象（固化了旧子版本）会继续按旧标记判定，表现成"改了配置不生效"
            applyRevisableToIterations(existing.getOid(), template.getStates());
            // 「状态 → 流程」配置按状态 code 继承到新子版本。
            // 必须做：模板每次编辑都整体重建状态行并生成新子版本，而配置挂的是子版本 ——
            // 不继承的话用户一编辑模板，配置就留在旧版本上（界面上看不到、实例也解析不到）。
            // 旧子版本的行保留不动 —— 在途实例固化的是旧子版本 oid，仍要能解析出当时那一版配置。
            typeLifecycleStateProcessService.inherit(oldLatest.getOid(), newIter.getOid());
        } else {
            // 首次更新时没有旧版本，直接创建初始版本
            LifecycleTemplateIteration newIter = (LifecycleTemplateIteration) createInitialIteration(existing);
            iterationMapper.insert(newIter);
            template.setOid(existing.getOid());
            saveChildren(template, newIter.getOid());
        }

        log.info("生命周期模板已更新: code={}", code);
        return findByCode(code);
    }

    @Override
    @Transactional
    public boolean delete(String code) {
        if (code == null || code.trim().isEmpty()) return false;
        String normalized = code.trim();
        LifecycleTemplateMaster existing = mapper.selectByCode(normalized);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "生命周期模板");
        // 级联删除：先删子数据，DB 外键 ON DELETE CASCADE 自动处理 iteration
        LifecycleTemplateIteration latestIter = iterationMapper.selectLatestByMasterOid(existing.getOid());
        if (latestIter != null) {
            mapper.deleteStateRefsByIterationOid(latestIter.getOid());
            mapper.deleteTransitionRefsByIterationOid(latestIter.getOid());
        }
        // 「状态 → 流程」配置只以子版本 oid 作软引用（无外键）：子版本会被级联删掉，
        // 配置行不主动清理就成了永远解析不到的垃圾
        List<LifecycleTemplateIteration> allIterations = iterationMapper.selectByMasterOid(existing.getOid());
        if (allIterations != null) {
            List<String> iterationOids = new ArrayList<>();
            for (LifecycleTemplateIteration iteration : allIterations) {
                iterationOids.add(iteration.getOid());
            }
            typeLifecycleStateProcessService.clearByIterations(iterationOids);
        }
        mapper.deleteByCode(normalized);
        log.info("生命周期模板已删除: code={}", normalized);
        return true;
    }

    @Override
    public LifecycleTemplateMaster findByCode(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        return fillChildren(mapper.selectByCode(code.trim()));
    }

    @Override
    public LifecycleTemplateMaster findByIterationOid(String templateIterationOid) {
        if (templateIterationOid == null || templateIterationOid.trim().isEmpty()) return null;
        LifecycleTemplateIteration iter = iterationMapper.selectByOid(templateIterationOid.trim());
        if (iter == null || iter.getMasterOid() == null || iter.getMasterOid().trim().isEmpty()) {
            return null;
        }
        LifecycleTemplateMaster master = mapper.selectByOid(iter.getMasterOid().trim());
        if (master == null) return null;
        // 注意：这里 latestIteration 放的是**传入的那一版**，states / transitions 也从它加载 ——
        // 对象固化的模板版本可能落后于最新版，候选必须与执行侧（loadDef(该版本)）同源
        master.setLatestIteration(iter);
        fillChildrenOf(master, iter.getOid());
        return master;
    }

    @Override
    public List<LifecycleTemplateMaster> findAll() {
        List<LifecycleTemplateMaster> list = mapper.selectAll();
        if (list != null) list.forEach(this::fillChildren);
        return list;
    }

    @Override
    public List<LifecycleTemplateMaster> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return findAll();
        List<LifecycleTemplateMaster> list = mapper.search(keyword.trim());
        if (list != null) list.forEach(this::fillChildren);
        return list;
    }

    @Override
    public boolean exists(String code) {
        return code != null && mapper.existsByCode(code.trim()) > 0;
    }

    // ==================== 类型绑定解析 ====================

    @Override
    public void initLifecycle(IterationEntity iter, String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) return;
        try {
            TypeDefinition typeDef = typeDefinitionMapper.selectByCode(
                    typeCode.trim(), TenantContext.get(), TenantContext.PLATFORM_TENANT_OID);
            if (typeDef == null) return;
            TypeLifecycleTemplateLink link = typeLifecycleTemplateLinkService.getByTypeOid(typeDef.getOid());
            if (link == null) return;
            LifecycleTemplateMaster template = findByCode(link.getLifecycleTemplateCode());
            if (template == null || template.getLatestIteration() == null) return;
            iter.setLifecycleTemplateIterationOid(template.getLatestIteration().getOid());
            String initialCode = null;
            if (template.getInitialStateCode() != null && !template.getInitialStateCode().trim().isEmpty()) {
                initialCode = template.getInitialStateCode().trim();
            } else if (template.getStates() != null && !template.getStates().isEmpty()) {
                initialCode = template.getStates().get(0).getStatusCode();
            }
            if (initialCode != null) {
                // status 列经 LifecycleStatusTypeHandler 存储的是 code，正常情况下应从字典查真实对象。
                // 但若字典缺少该状态（如 STANDARD/SIMPLE 的初始状态 DRAFT 曾长期缺失），
                // 此前会静默跳过 → status 列为 NULL、列表状态空白，且无任何告警难以察觉。
                // 现改为：查不到则按 code 兜底写入，并明确告警提示补全状态字典。
                LifecycleStatus initStatus = lifecycleStatusMapper.selectByCode(initialCode);
                if (initStatus == null) {
                    log.warn("生命周期初始状态未在 ck_lifecycle_status 中定义，已按 code 兜底写入: "
                                    + "initialCode={}, templateCode={}。请在状态字典中补全该状态，"
                                    + "否则列表状态名无法解析",
                            initialCode, link.getLifecycleTemplateCode());
                    initStatus = new LifecycleStatus(initialCode, initialCode);
                    initStatus.setDisplayName(initialCode);
                }
                iter.setStatus(initStatus);
            }
        } catch (Exception e) {
            log.warn("生命周期初始化失败，跳过: typeCode={}, error={}", typeCode, e.getMessage());
        }
    }

    // ==================== 目标状态迁移 ====================

    @Override
    public String rebindLifecycle(IterationEntity iteration, String templateIterationOid) {
        if (iteration == null) {
            throw new IllegalArgumentException("迭代对象不能为空");
        }
        String targetOid = templateIterationOid == null ? "" : templateIterationOid.trim();
        if (targetOid.isEmpty()) {
            throw new IllegalArgumentException("目标生命周期模板不能为空");
        }
        LifecycleTemplateIteration targetIter = iterationMapper.selectByOid(targetOid);
        if (targetIter == null) {
            throw new IllegalArgumentException("生命周期模板子版本不存在: " + targetOid);
        }
        LifecycleTemplateDef def = loadDef(targetOid);
        if (def.getStatuses().isEmpty()) {
            throw new IllegalStateException("目标生命周期模板没有定义任何状态: " + targetOid);
        }
        // 状态按 code 迁移：新模板里有同名状态就保留（多数模板的 DRAFT/RELEASED 是共通的）；
        // 没有就落到新模板的初始状态 —— 绝不硬留一个新模板不认识的 code（那会让对象"卡死"）
        String current = iteration.getStatus() != null ? iteration.getStatus().getCode() : null;
        boolean keepsStatus = current != null
                && def.getStatuses().stream().anyMatch(s -> current.equals(s.getCode()));
        final String targetCode = keepsStatus ? current : initialCodeOf(targetIter, def);
        LifecycleStatus ref = def.getStatuses().stream()
                .filter(s -> targetCode.equals(s.getCode()))
                .findFirst()
                .orElse(new LifecycleStatus(targetCode, targetCode));
        iteration.setLifecycleTemplateIterationOid(targetOid);
        iteration.setStatus(resolveStatus(ref));
        log.info("生命周期模板迁移: templateIteration={}, 状态 {} → {}", targetOid, current, targetCode);
        return targetCode;
    }

    /** 模板的初始状态：优先取主档上显式配置的，其次取状态列表的第一个（与 initLifecycle 一致） */
    private String initialCodeOf(LifecycleTemplateIteration targetIter, LifecycleTemplateDef def) {
        return initialStatusOf(targetIter != null ? targetIter.getOid() : null, def).getCode();
    }

    /** 同 {@link #initialCodeOf}，返回状态对象（带显示名），供退回初始状态时使用 */
    private LifecycleStatus initialStatusOf(String templateIterationOid, LifecycleTemplateDef def) {
        if (templateIterationOid != null && !templateIterationOid.trim().isEmpty()) {
            LifecycleTemplateIteration iter = iterationMapper.selectByOid(templateIterationOid.trim());
            if (iter != null && iter.getMasterOid() != null && !iter.getMasterOid().trim().isEmpty()) {
                LifecycleTemplateMaster master = mapper.selectByOid(iter.getMasterOid().trim());
                if (master != null && master.getInitialStateCode() != null
                        && !master.getInitialStateCode().trim().isEmpty()) {
                    String configured = master.getInitialStateCode().trim();
                    for (LifecycleStatus status : def.getStatuses()) {
                        if (configured.equals(status.getCode())) {
                            return status;
                        }
                    }
                }
            }
        }
        return def.getStatuses().get(0);
    }

    @Override
    public void ensureRevisable(IterationEntity iteration) {
        if (iteration == null) {
            return;
        }
        String templateIterationOid = iteration.getLifecycleTemplateIterationOid();
        String statusCode = iteration.getStatus() != null ? iteration.getStatus().getCode() : null;
        if (isBlank(templateIterationOid) || isBlank(statusCode)) {
            // 历史数据没固化模板 / 没有状态：与 RevisionRuleGuard 同口径，配置缺失不阻断业务动作
            return;
        }
        LifecycleTemplateStatusRef state;
        try {
            List<LifecycleTemplateStatusRef> refs =
                    mapper.selectStateRefsByIterationOid(templateIterationOid.trim());
            state = refs == null ? null : refs.stream()
                    .filter(r -> statusCode.equals(r.getStatusCode()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("修订状态校验跳过（模板状态读取失败）: templateIteration={}, status={}, error={}",
                    templateIterationOid, statusCode, e.getMessage());
            return;
        }
        // 状态已不在该版模板内（模板被编辑过）：交给迁移规则去判，这里不越权解释
        if (state == null || state.isRevisable()) {
            return;
        }
        String statusName = isBlank(state.getStatusDisplayName())
                ? statusCode : state.getStatusDisplayName();
        throw new IllegalStateException("对象当前状态「" + statusName + "」不允许修订。"
                + "请先通过「设置生命周期状态」切换到允许修订的状态；"
                + "或由管理员在「业务配置 → 生命周期」中把该状态设为可修订。");
    }

    @Override
    @Transactional
    public void setStateRevisable(String templateCode, String statusCode, boolean revisable) {
        if (isBlank(templateCode) || isBlank(statusCode)) {
            throw new IllegalArgumentException("模板编码与状态编码不能为空");
        }
        String code = templateCode.trim();
        String status = statusCode.trim();
        LifecycleTemplateMaster existing = mapper.selectByCode(code);
        if (existing == null) {
            throw new IllegalArgumentException("生命周期模板 '" + code + "' 不存在");
        }
        // 平台模板只有平台管理员能改；租户模板由本租户管理员改（与模板结构编辑同一道闸）
        TenantContext.requireEditPermission(existing.getTenantOid(), "生命周期模板");

        List<LifecycleTemplateIteration> iterations = iterationMapper.selectByMasterOid(existing.getOid());
        if (iterations == null || iterations.isEmpty()) {
            throw new IllegalStateException("生命周期模板 '" + code + "' 没有可用子版本");
        }
        boolean found = false;
        for (LifecycleTemplateIteration iter : iterations) {
            List<LifecycleTemplateStatusRef> states = mapper.selectStateRefsByIterationOid(iter.getOid());
            if (states == null) {
                continue;
            }
            for (LifecycleTemplateStatusRef ref : states) {
                if (!status.equals(ref.getStatusCode())) {
                    continue;
                }
                found = true;
                // 全部子版本一起改：在途对象固化的可能是旧子版本，只改最新版等于"改了配置却不生效"
                mapper.updateStateRevisable(iter.getOid(), status, revisable);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("状态 " + status + " 不在生命周期模板 '" + code + "' 内");
        }
        log.info("生命周期状态可修订已更新: template={}, status={}, revisable={}", code, status, revisable);
    }

    @Override
    public LifecycleStatus initialStatus(String templateIterationOid) {
        if (isBlank(templateIterationOid)) {
            return null;
        }
        try {
            LifecycleTemplateDef def = loadDef(templateIterationOid.trim());
            if (def == null || def.getStatuses() == null || def.getStatuses().isEmpty()) {
                return null;
            }
            return resolveStatus(initialStatusOf(templateIterationOid.trim(), def));
        } catch (Exception e) {
            log.warn("解析模板初始状态失败: templateIteration={}, error={}", templateIterationOid, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Boolean> revisableFlags(String templateIterationOid) {
        Map<String, Boolean> flags = new HashMap<>();
        if (isBlank(templateIterationOid)) {
            return flags;
        }
        try {
            List<LifecycleTemplateStatusRef> refs = mapper.selectStateRefsByIterationOid(templateIterationOid.trim());
            if (refs != null) {
                for (LifecycleTemplateStatusRef ref : refs) {
                    if (ref.getStatusCode() != null) {
                        flags.put(ref.getStatusCode(), ref.isRevisable());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取状态可修订标记失败，按允许处理: templateIteration={}, error={}",
                    templateIterationOid, e.getMessage());
        }
        return flags;
    }

    @Override
    public void moveToState(IterationEntity iteration, String targetStateCode) {
        if (iteration == null) {
            throw new IllegalArgumentException("迭代对象不能为空");
        }
        String target = targetStateCode == null ? "" : targetStateCode.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("目标状态不能为空");
        }
        String templateIterationOid = iteration.getLifecycleTemplateIterationOid();
        if (templateIterationOid == null || templateIterationOid.trim().isEmpty()) {
            throw new IllegalStateException(
                    "该对象未绑定生命周期模板，无法判断能否迁到状态 " + target + "（请先为它的类型绑定生命周期模板）");
        }

        LifecycleTemplateDef def = loadDef(templateIterationOid);
        boolean defined = def.getStatuses().stream().anyMatch(s -> target.equals(s.getCode()));
        if (!defined) {
            throw new IllegalArgumentException("目标状态 " + target + " 不在该对象的生命周期模板内"
                    + "（模板允许的状态：" + statusCodes(def) + "）");
        }

        String current = iteration.getStatus() != null ? iteration.getStatus().getCode() : null;
        if (current == null) {
            // 没有当前状态（历史数据 / 初始化未跑）：只允许落到模板的第一个状态，避免"从无到有"直接跳到最后
            String initial = def.getStatuses().isEmpty() ? null : def.getStatuses().get(0).getCode();
            if (!target.equals(initial)) {
                throw new IllegalArgumentException(
                        "该对象当前没有生命周期状态，只能迁移到模板的初始状态 " + initial + "，不能直接设为 " + target);
            }
        } else if (!target.equals(current) && !isAllowed(def, current, target)) {
            // 同状态重复设置视为幂等（流程里同一状态可能被两个分支先后设置）
            throw new IllegalArgumentException("当前状态 " + current + " 不能迁移到 " + target
                    + "（模板允许：升版 → " + orDash(def.getTransitions().get(current))
                    + "，回退 → " + orDash(def.getRejections().get(current)) + "）");
        }

        LifecycleStatus status = lifecycleStatusMapper.selectByCode(target);
        if (status == null) {
            // 与 initLifecycle 同一套兜底：字典缺该状态时按 code 落库并告警，不静默变成空状态
            log.warn("生命周期状态未在 ck_lifecycle_status 中定义，已按 code 兜底写入: code={}", target);
            status = new LifecycleStatus(target, target);
            status.setDisplayName(target);
        }
        iteration.setStatus(status);
        log.info("生命周期状态迁移: {} → {}（模板子版本 {}）", current, target, templateIterationOid);
    }

    @Override
    public void moveToInitialState(IterationEntity iteration) {
        if (iteration == null) {
            throw new IllegalArgumentException("迭代对象不能为空");
        }
        String templateIterationOid = iteration.getLifecycleTemplateIterationOid();
        if (templateIterationOid == null || templateIterationOid.trim().isEmpty()) {
            throw new IllegalStateException("该对象未绑定生命周期模板，无法确定初始状态");
        }
        LifecycleTemplateDef def = loadDef(templateIterationOid);
        if (def.getStatuses().isEmpty()) {
            throw new IllegalStateException("生命周期模板里没有定义任何状态: " + templateIterationOid);
        }
        // 与 initLifecycle / 候选接口同口径：优先主档上显式配置的初始状态，其次状态清单第一个。
        // 两处不一致时，迁移把对象放到显式初始状态、退回却按另一个状态走，会直接对不上
        LifecycleStatus initial = initialStatusOf(templateIterationOid, def);
        String current = iteration.getStatus() != null ? iteration.getStatus().getCode() : null;
        if (current == null || current.equals(initial.getCode())) {
            iteration.setStatus(resolveStatus(initial));
            log.info("生命周期状态已是初始状态，无需迁移: {}（模板子版本 {}）", initial.getCode(), templateIterationOid);
            return;
        }

        // 逐跳回退：多跳（PUBLISHED→WORKING→DRAFT）在模板里没有直接规则，
        // 允许一步跳过去就等于把回退链当摆设 —— 那正是模板要防的事
        Set<String> visited = new LinkedHashSet<>();
        visited.add(current);
        String walk = current;
        while (!initial.getCode().equals(walk)) {
            String prev = def.getRejections().get(walk);
            if (prev == null) {
                throw new IllegalArgumentException("当前状态 " + current + " 不能退回初始状态 " + initial.getCode()
                        + "：状态 " + walk + " 没有定义回退规则（模板：" + def.getName() + "）");
            }
            if (!visited.add(prev)) {
                throw new IllegalStateException("生命周期模板的回退规则成环，无法退回初始状态: "
                        + String.join(" → ", visited) + " → " + prev);
            }
            walk = prev;
        }
        iteration.setStatus(resolveStatus(initial));
        log.info("生命周期状态退回初始状态: {} → {}（沿回退链，模板子版本 {}）",
                current, initial.getCode(), templateIterationOid);
    }

    /** 按 code 取状态对象（字典缺失时兜底按 code 建一个并告警，与 initLifecycle 同一套口径） */
    private LifecycleStatus resolveStatus(LifecycleStatus ref) {
        LifecycleStatus status = lifecycleStatusMapper.selectByCode(ref.getCode());
        if (status != null) {
            return status;
        }
        log.warn("生命周期状态未在 ck_lifecycle_status 中定义，已按 code 兜底写入: code={}", ref.getCode());
        LifecycleStatus fallback = new LifecycleStatus(ref.getCode(), ref.getCode());
        fallback.setDisplayName(ref.getDisplayName() != null ? ref.getDisplayName() : ref.getCode());
        return fallback;
    }

    /** 由模板子版本 oid 装配内存状态机（状态 + 升版规则 + 回退规则） */
    private LifecycleTemplateDef loadDef(String templateIterationOid) {
        LifecycleTemplateDef def = new LifecycleTemplateDef(templateIterationOid);
        List<LifecycleTemplateStatusRef> states = mapper.selectStateRefsByIterationOid(templateIterationOid);
        for (LifecycleTemplateStatusRef ref : states != null ? states : List.<LifecycleTemplateStatusRef>of()) {
            def.addStatus(new LifecycleStatus(ref.getStatusCode(), ref.getStatusDisplayName()));
        }
        List<LifecycleTemplateTransitionRef> transitions = mapper.selectTransitionRefsByIterationOid(templateIterationOid);
        for (LifecycleTemplateTransitionRef ref : transitions != null
                ? transitions : List.<LifecycleTemplateTransitionRef>of()) {
            if ("REJECT".equals(ref.getTransitionType())) {
                def.addRejection(ref.getFromStatusCode(), ref.getToStatusCode());
            } else {
                def.addTransition(ref.getFromStatusCode(), ref.getToStatusCode());
            }
        }
        return def;
    }

    private static boolean isAllowed(LifecycleTemplateDef def, String from, String to) {
        return to.equals(def.getTransitions().get(from)) || to.equals(def.getRejections().get(from));
    }

    private static String statusCodes(LifecycleTemplateDef def) {
        List<String> codes = new ArrayList<>();
        for (LifecycleStatus status : def.getStatuses()) {
            codes.add(status.getCode());
        }
        return String.join(" / ", codes);
    }

    private static String orDash(String code) {
        return code == null ? "（无）" : code;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ==================== 内部方法 ====================

    private void saveChildren(LifecycleTemplateMaster template, String iterationOid) {
        // 状态关联
        if (template.getStates() != null) {
            for (LifecycleTemplateStatusRef ref : template.getStates()) {
                ref.setOid(UUID.randomUUID().toString());
                ref.setIterationOid(iterationOid);
                mapper.insertStateRef(ref);
            }
        }
        // 流转规则（升版）
        if (template.getTransitions() != null) {
            for (LifecycleTemplateTransitionRef ref : template.getTransitions()) {
                ref.setOid(UUID.randomUUID().toString());
                ref.setIterationOid(iterationOid);
                ref.setTransitionType("PROMOTE");
                mapper.insertTransitionRef(ref);
            }
        }
        // 流转规则（驳回）
        if (template.getRejections() != null) {
            for (LifecycleTemplateTransitionRef ref : template.getRejections()) {
                ref.setOid(UUID.randomUUID().toString());
                ref.setIterationOid(iterationOid);
                ref.setTransitionType("REJECT");
                mapper.insertTransitionRef(ref);
            }
        }
    }

    /**
     * 把「可修订」标记同步到该模板的全部子版本。
     *
     * <p>权限开关对在途对象同样要生效：它们固化的是旧子版本，只写新版本等于没改。
     * 旧版本里没有该状态时 UPDATE 自然不命中，无需特殊处理。
     */
    private void applyRevisableToIterations(String masterOid, List<LifecycleTemplateStatusRef> states) {
        if (masterOid == null || states == null || states.isEmpty()) {
            return;
        }
        List<LifecycleTemplateIteration> iterations = iterationMapper.selectByMasterOid(masterOid);
        if (iterations == null) {
            return;
        }
        for (LifecycleTemplateIteration iter : iterations) {
            for (LifecycleTemplateStatusRef ref : states) {
                if (isBlank(ref.getStatusCode())) {
                    continue;
                }
                mapper.updateStateRevisable(iter.getOid(), ref.getStatusCode(), ref.isRevisable());
            }
        }
    }

    private LifecycleTemplateMaster fillChildren(LifecycleTemplateMaster template) {
        if (template == null) return null;
        // 加载最新子版本
        LifecycleTemplateIteration latestIter = iterationMapper.selectLatestByMasterOid(template.getOid());
        template.setLatestIteration(latestIter);
        // 通过子版本 oid 加载状态和流转规则
        if (latestIter != null) {
            fillChildrenOf(template, latestIter.getOid());
        }
        return template;
    }

    /** 按指定子版本 oid 装载 states / transitions / rejections（最新版与"对象固化的那一版"共用） */
    private void fillChildrenOf(LifecycleTemplateMaster template, String iterationOid) {
        List<LifecycleTemplateStatusRef> states = mapper.selectStateRefsByIterationOid(iterationOid);
        template.setStates(states != null ? states : Collections.emptyList());

        List<LifecycleTemplateTransitionRef> allTrans = mapper.selectTransitionRefsByIterationOid(iterationOid);
        List<LifecycleTemplateTransitionRef> promotes = new ArrayList<>();
        List<LifecycleTemplateTransitionRef> rejects = new ArrayList<>();
        if (allTrans != null) {
            for (LifecycleTemplateTransitionRef t : allTrans) {
                if ("REJECT".equals(t.getTransitionType())) {
                    rejects.add(t);
                } else {
                    promotes.add(t);
                }
            }
        }
        template.setTransitions(promotes);
        template.setRejections(rejects);
    }
}
