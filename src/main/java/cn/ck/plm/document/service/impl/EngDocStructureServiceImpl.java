/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.impl;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.document.entity.EngDocDependencyLink;
import cn.ck.plm.document.entity.EngDocMemberLink;
import cn.ck.plm.document.entity.EngDocRefLink;
import cn.ck.plm.document.entity.EngDocRefType;
import cn.ck.plm.document.mapper.EngDocMemberLinkMapper;
import cn.ck.plm.document.mapper.EngDocRefLinkMapper;
import cn.ck.plm.document.service.api.EngDocStructureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * {@link EngDocStructureService} 实现 —— 对齐 Windchill {@code EPMStructureService}。
 *
 * <p>本实现只依赖两张链接表的 Mapper，不反向依赖工程数据主数据服务；
 * 需要下钻时由调用方注入「主对象 → 迭代」解析函数。
 */
@Service
public class EngDocStructureServiceImpl implements EngDocStructureService {

    private static final Logger log = LoggerFactory.getLogger(EngDocStructureServiceImpl.class);

    private final EngDocMemberLinkMapper memberMapper;
    private final EngDocRefLinkMapper refMapper;

    public EngDocStructureServiceImpl(EngDocMemberLinkMapper memberMapper,
                                      EngDocRefLinkMapper refMapper) {
        this.memberMapper = memberMapper;
        this.refMapper = refMapper;
    }

    // ==================== 装配结构（EPMMemberLink） ====================

    @Override
    @Transactional
    public EngDocMemberLink addMember(String assemblyIterationOid, String childMasterOid, BigDecimal quantity) {
        EngDocMemberLink link = new EngDocMemberLink();
        link.setUsedByIterationOid(assemblyIterationOid);
        link.setUsesMasterOid(childMasterOid);
        link.setQuantity(quantity == null ? BigDecimal.ONE : quantity);
        return addMember(link);
    }

    @Override
    @Transactional
    public EngDocMemberLink addMember(EngDocMemberLink link) {
        if (link.getUsedByIterationOid() == null || link.getUsesMasterOid() == null) {
            throw new IllegalArgumentException("成员链接必须同时指定父装配迭代（used_by_iteration_oid）与子件主对象（uses_master_oid）");
        }
        if (link.getQuantity() == null) {
            link.setQuantity(BigDecimal.ONE);
        }
        if (!link.isConsistent()) {
            throw new IllegalArgumentException("成员链接不满足「带 transform ⇒ quantity = 1 且 placed = true」"
                    + "（对齐 Windchill EPMMemberLink.checkAttributes）");
        }
        touch(link);
        memberMapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public EngDocMemberLink updateMember(EngDocMemberLink link) {
        if (link.getOid() == null) {
            throw new IllegalArgumentException("更新成员链接必须提供 oid");
        }
        if (!link.isConsistent()) {
            throw new IllegalArgumentException("成员链接不满足「带 transform ⇒ quantity = 1 且 placed = true」");
        }
        touch(link);
        memberMapper.update(link);
        return link;
    }

    @Override
    @Transactional
    public boolean removeMember(String linkOid) {
        return memberMapper.deleteByOid(linkOid) > 0;
    }

    @Override
    @Transactional
    public int clearMembers(String assemblyIterationOid) {
        return memberMapper.deleteByUsedByIterationOid(assemblyIterationOid);
    }

    @Override
    public List<EngDocMemberLink> childrenOf(String assemblyIterationOid) {
        return memberMapper.selectByUsedByIterationOid(assemblyIterationOid);
    }

    @Override
    public List<EngDocMemberLink> parentsOf(String childMasterOid) {
        return memberMapper.selectByUsesMasterOid(childMasterOid);
    }

    // ==================== 横向引用（EPMReferenceLink） ====================

    @Override
    @Transactional
    public EngDocRefLink addReference(String referencedByIterationOid, String referencesMasterOid, EngDocRefType type) {
        EngDocRefLink link = new EngDocRefLink();
        link.setReferencedByIterationOid(referencedByIterationOid);
        link.setReferencesMasterOid(referencesMasterOid);
        link.setReferenceType((type == null ? EngDocRefType.DEPENDENCY : type).name());
        return addReference(link);
    }

    @Override
    @Transactional
    public EngDocRefLink addReference(EngDocRefLink link) {
        if (link.getReferencedByIterationOid() == null || link.getReferencesMasterOid() == null) {
            throw new IllegalArgumentException("引用链接必须同时指定发起引用的迭代（referenced_by_iteration_oid）"
                    + "与被引用主对象（references_master_oid）");
        }
        if (isBlank(link.getReferenceType())) {
            link.setReferenceType(EngDocRefType.DEPENDENCY.name());
        }
        if (isBlank(link.getReferencesType())) {
            link.setReferencesType(EngDocRefLink.REFERENCES_TYPE_ENG_DOCUMENT);
        }
        touch(link);
        refMapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public EngDocRefLink updateReference(EngDocRefLink link) {
        if (link.getOid() == null) {
            throw new IllegalArgumentException("更新引用链接必须提供 oid");
        }
        touch(link);
        refMapper.update(link);
        return link;
    }

    @Override
    @Transactional
    public boolean removeReference(String linkOid) {
        return refMapper.deleteByOid(linkOid) > 0;
    }

    @Override
    @Transactional
    public int clearReferences(String referencedByIterationOid) {
        return refMapper.deleteByReferencedByIterationOid(referencedByIterationOid);
    }

    @Override
    public List<EngDocRefLink> referencesOf(String referencedByIterationOid) {
        return refMapper.selectByReferencedByIterationOid(referencedByIterationOid);
    }

    @Override
    public List<EngDocRefLink> referencedBy(String referencesMasterOid) {
        return refMapper.selectByReferencesMasterOid(referencesMasterOid);
    }

    @Override
    public List<EngDocRefLink> referencesOfType(EngDocRefType type) {
        return refMapper.selectByReferenceType((type == null ? EngDocRefType.DEPENDENCY : type).name());
    }

    // ==================== 递归导航 ====================

    @Override
    public List<String> expandDescendantMasters(String assemblyIterationOid, int maxDepth,
                                                Function<String, String> masterToIteration) {
        List<String> result = new ArrayList<>();
        if (assemblyIterationOid == null) {
            return result;
        }
        Set<String> visitedMasters = new HashSet<>();
        List<String> levelIterations = new ArrayList<>();
        levelIterations.add(assemblyIterationOid);

        for (int level = 0; level < normalizeDepth(maxDepth) && !levelIterations.isEmpty(); level++) {
            List<String> nextIterations = new ArrayList<>();
            for (String iterOid : levelIterations) {
                for (EngDocMemberLink m : memberMapper.selectByUsedByIterationOid(iterOid)) {
                    String childMaster = m.getUsesMasterOid();
                    if (childMaster == null || !visitedMasters.add(childMaster)) {
                        continue;
                    }
                    result.add(childMaster);
                    if (masterToIteration != null) {
                        String childIter = masterToIteration.apply(childMaster);
                        if (!isBlank(childIter)) {
                            nextIterations.add(childIter);
                        }
                    }
                }
            }
            levelIterations = nextIterations;
        }
        return result;
    }

    @Override
    public List<String> collectDependencyMasters(String rootIterationOid, int maxDepth,
                                                 Function<String, String> masterToIteration) {
        List<String> result = new ArrayList<>();
        if (rootIterationOid == null) {
            return result;
        }
        Set<String> visitedMasters = new HashSet<>();
        List<String> levelIterations = new ArrayList<>();
        levelIterations.add(rootIterationOid);

        for (int level = 0; level < normalizeDepth(maxDepth) && !levelIterations.isEmpty(); level++) {
            List<String> nextIterations = new ArrayList<>();
            for (String iterOid : levelIterations) {
                for (EngDocRefLink r : refMapper.selectByReferencedByIterationOid(iterOid)) {
                    String refMaster = r.getReferencesMasterOid();
                    if (refMaster == null || !visitedMasters.add(refMaster)) {
                        continue;
                    }
                    result.add(refMaster);
                    // 仅工程数据可继续下钻（通用文档无工程数据结构）
                    if (masterToIteration != null && !r.isReferencesDocument()) {
                        String nextIter = masterToIteration.apply(refMaster);
                        if (!isBlank(nextIter)) {
                            nextIterations.add(nextIter);
                        }
                    }
                }
            }
            levelIterations = nextIterations;
        }
        return result;
    }

    // ==================== 泛化（面向契约接口） ====================

    @Override
    public List<EngDocDependencyLink> allDependenciesOfIteration(String iterationOid) {
        List<EngDocDependencyLink> all = new ArrayList<>();
        if (iterationOid == null) {
            return all;
        }
        all.addAll(memberMapper.selectByUsedByIterationOid(iterationOid));
        all.addAll(refMapper.selectByReferencedByIterationOid(iterationOid));
        log.debug("迭代 {} 共 {} 条依赖链接（成员 + 引用）", iterationOid, all.size());
        return all;
    }

    // ==================== 内部工具 ====================

    /** 层数下限保护：<=0 视为 1（仅直接成员） */
    private int normalizeDepth(int maxDepth) {
        return maxDepth <= 0 ? 1 : maxDepth;
    }

    /** 填充审计时间戳（creator / tenantOid 由 AuditInterceptor 自动填充） */
    private void touch(BaseEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
