/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.*;
import cn.ck.plm.base.mapper.LifecycleTemplateIterationMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
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

    public DefaultLifecycleTemplateService(LifecycleTemplateMapper mapper,
                                           LifecycleTemplateIterationMapper iterationMapper,
                                           TypeDefinitionMapper typeDefinitionMapper,
                                           TypeLifecycleTemplateLinkService typeLifecycleTemplateLinkService) {
        this.mapper = mapper;
        this.iterationMapper = iterationMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.typeLifecycleTemplateLinkService = typeLifecycleTemplateLinkService;
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
                iter.setStatus(new LifecycleStatus(initialCode, initialCode));
            }
        } catch (Exception e) {
            log.warn("生命周期初始化失败，跳过: typeCode={}, error={}", typeCode, e.getMessage());
        }
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

    private LifecycleTemplateMaster fillChildren(LifecycleTemplateMaster template) {
        if (template == null) return null;
        // 加载最新子版本
        LifecycleTemplateIteration latestIter = iterationMapper.selectLatestByMasterOid(template.getOid());
        template.setLatestIteration(latestIter);
        // 通过子版本 oid 加载状态和流转规则
        if (latestIter != null) {
            String iterationOid = latestIter.getOid();
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
        return template;
    }
}
