/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.document.entity.EngDocPartLink;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;
import cn.ck.plm.document.mapper.EngDocPartLinkMapper;
import cn.ck.plm.document.mapper.EngineeringDocumentIterationMapper;
import cn.ck.plm.document.mapper.EngineeringDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EngDocPartLinkServiceImpl implements EngDocPartLinkService {

    private static final Logger log = LoggerFactory.getLogger(EngDocPartLinkServiceImpl.class);

    private final EngDocPartLinkMapper linkMapper;
    private final EngineeringDocumentIterationMapper engIterationMapper;
    private final EngineeringDocumentMapper engDocumentMapper;

    public EngDocPartLinkServiceImpl(EngDocPartLinkMapper linkMapper,
                                     EngineeringDocumentIterationMapper engIterationMapper,
                                     EngineeringDocumentMapper engDocumentMapper) {
        this.linkMapper = linkMapper;
        this.engIterationMapper = engIterationMapper;
        this.engDocumentMapper = engDocumentMapper;
    }

    // ==================== 查询 ====================

    @Override
    public List<EngDocPartLink> findByPartIteration(String partIterationOid) {
        return linkMapper.selectByPartIterationOid(partIterationOid);
    }

    @Override
    public List<EngDocPartLink> findByEngDocument(String engDocumentOid) {
        return linkMapper.selectByEngDocumentOid(engDocumentOid);
    }

    @Override
    public List<EngDocPartLink> findByEngDocIteration(String engDocIterationOid) {
        return linkMapper.selectByEngDocIterationOid(engDocIterationOid);
    }

    @Override
    public EngDocPartLink findById(String oid) {
        return linkMapper.selectByOid(oid);
    }

    // ==================== 写入 ====================

    @Override
    @Transactional
    public EngDocPartLink create(EngDocPartLink link) {
        assertCreateFields(link);
        link.setAssocType(normalizeAssocType(link.getAssocType()));
        link.setLinkType(resolveLinkType(link));
        resolveEngDocIteration(link);
        // 2026-11-18 用户口径：一个零件版本的每类 CAD 关系（符号 / 封装 / Datasheet / 三维 /
        // 二维 / 工程图）至多一条。同类型再次提交视为「更换关联对象」——直接更新已有那条，
        // 而不是插第二条。不同类别之间互不影响。
        EngDocPartLink sameType = findByPartIterationAndType(
                link.getPartIterationOid(), link.getLinkType(), null);
        if (sameType != null) {
            assertNoDuplicate(link.getPartIterationOid(), link.getEngDocumentOid(), sameType.getOid());
            assertSingletonAssocType(link.getPartIterationOid(), link.getAssocType(), sameType.getOid());
            sameType.setEngDocumentOid(link.getEngDocumentOid());
            sameType.setEngDocIterationOid(link.getEngDocIterationOid());
            sameType.setAssocType(link.getAssocType());
            sameType.setBuildStructure(link.isBuildStructure());
            sameType.setBuildAttribute(link.isBuildAttribute());
            sameType.setBuildRepresentation(link.isBuildRepresentation());
            sameType.setAssocSource(link.getAssocSource() == null || link.getAssocSource().isEmpty()
                    ? sameType.getAssocSource() : link.getAssocSource());
            sameType.setPrimary(link.isPrimary());
            sameType.setSortOrder(link.getSortOrder());
            sameType.setUpdater(UserContext.get());
            sameType.setUpdatedAt(LocalDateTime.now());
            linkMapper.update(sameType);
            log.info("更换工程数据-零件构建规则: partIterationOid={}, linkType={}, engDocumentOid={}",
                    sameType.getPartIterationOid(), sameType.getLinkType(), sameType.getEngDocumentOid());
            return sameType;
        }

        assertNoDuplicate(link.getPartIterationOid(), link.getEngDocumentOid(), null);
        assertSingletonAssocType(link.getPartIterationOid(), link.getAssocType(), null);

        if (link.getOid() == null || link.getOid().isEmpty()) {
            link.setOid(UUID.randomUUID().toString());
        }
        if (link.getAssocSource() == null || link.getAssocSource().isEmpty()) {
            link.setAssocSource("MANUAL");
        }
        link.setTenantOid(TenantContext.get());
        if (link.getCreator() == null || link.getCreator().isEmpty()) {
            link.setCreator(UserContext.get());
        }
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        linkMapper.insert(link);

        log.info("建立工程数据-零件构建规则: partIterationOid={}, engDocumentOid={}, assocType={}",
                link.getPartIterationOid(), link.getEngDocumentOid(), link.getAssocType());
        return link;
    }

    /**
     * 更新构建规则。
     *
     * <p>两端（零件版本、工程数据主对象）不允许改：换对象就是另一条关系，应当删掉重建，
     * 否则历史留痕会指向错误的对象。
     */
    @Override
    @Transactional
    public EngDocPartLink update(EngDocPartLink link) {
        EngDocPartLink existing = linkMapper.selectByOid(link.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("构建规则不存在: " + link.getOid());
        }
        String assocType = normalizeAssocType(link.getAssocType());
        assertSingletonAssocType(existing.getPartIterationOid(), assocType, existing.getOid());

        existing.setAssocType(assocType);
        existing.setLinkType(link.getLinkType() == null || link.getLinkType().trim().isEmpty()
                ? existing.getLinkType() : normalizeLinkType(link.getLinkType()));
        if (link.getEngDocIterationOid() != null && !link.getEngDocIterationOid().trim().isEmpty()) {
            link.setEngDocumentOid(existing.getEngDocumentOid());
            resolveEngDocIteration(link);
            existing.setEngDocIterationOid(link.getEngDocIterationOid());
        }
        assertIterationLinkDuplicate(existing, existing.getOid());
        existing.setBuildStructure(link.isBuildStructure());
        existing.setBuildAttribute(link.isBuildAttribute());
        existing.setBuildRepresentation(link.isBuildRepresentation());
        existing.setAssocSource(link.getAssocSource() == null ? existing.getAssocSource() : link.getAssocSource());
        existing.setPrimary(link.isPrimary());
        existing.setSortOrder(link.getSortOrder());
        existing.setUpdater(UserContext.get());
        existing.setUpdatedAt(LocalDateTime.now());
        linkMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        linkMapper.deleteByOid(oid);
        log.info("删除工程数据-零件构建规则: oid={}", oid);
    }

    // ==================== 校验 ====================

    private void assertCreateFields(EngDocPartLink link) {
        if (link.getPartIterationOid() == null || link.getPartIterationOid().trim().isEmpty()) {
            throw new IllegalArgumentException("缺少零件迭代 oid");
        }
        if (link.getEngDocumentOid() == null || link.getEngDocumentOid().trim().isEmpty()) {
            throw new IllegalArgumentException("缺少工程数据 oid");
        }
    }

    /** 关联类型归一化 + 合法性校验 */
    private String normalizeAssocType(String assocType) {
        String type = (assocType == null || assocType.trim().isEmpty())
                ? EngDocPartLink.ASSOC_CONTENT
                : assocType.trim().toUpperCase();
        if (!EngDocPartLink.isValidAssocType(type)) {
            throw new IllegalArgumentException("不支持的关联类型: " + assocType);
        }
        return type;
    }

    /** 关系类型归一化：空值时按工程数据对象的软类型推断（三维/通用都归 ENG_DOCUMENT） */
    private String resolveLinkType(EngDocPartLink link) {
        if (link.getLinkType() != null && !link.getLinkType().trim().isEmpty()) {
            return normalizeLinkType(link.getLinkType());
        }
        EngineeringDocument doc = engDocumentMapper.selectByOid(link.getEngDocumentOid());
        String code = doc == null ? null : doc.getTypeDefinitionCode();
        if (code != null) {
            String upper = code.trim().toUpperCase();
            if (EngDocPartLink.isValidLinkType(upper)) {
                return upper;
            }
        }
        return EngDocPartLink.LINK_ENG_DOCUMENT;
    }

    private String normalizeLinkType(String linkType) {
        String type = linkType.trim().toUpperCase();
        if (!EngDocPartLink.isValidLinkType(type)) {
            throw new IllegalArgumentException("不支持的关系类型: " + linkType);
        }
        return type;
    }

    /** 把关系锁定到工程数据的具体迭代；没传就取最新版本，避免库里再出现 NULL */
    private void resolveEngDocIteration(EngDocPartLink link) {
        String iterOid = link.getEngDocIterationOid();
        EngineeringDocumentIteration iteration = (iterOid == null || iterOid.trim().isEmpty())
                ? engIterationMapper.selectLatestByMasterOid(link.getEngDocumentOid())
                : engIterationMapper.selectByOid(iterOid.trim());
        if (iteration == null || iteration.getOid() == null) {
            throw new IllegalArgumentException("工程数据没有可用迭代，无法建立 iteration ↔ iteration 关系: "
                    + link.getEngDocumentOid());
        }
        if (!link.getEngDocumentOid().equals(iteration.getMasterOid())) {
            throw new IllegalArgumentException("工程数据迭代不属于该工程数据主对象: " + iterOid);
        }
        link.setEngDocIterationOid(iteration.getOid());
    }

    /** 同一零件迭代 + 同一关系类型只允许一条；返回已存在的那条（用于「更换」） */
    private EngDocPartLink findByPartIterationAndType(String partIterationOid, String linkType, String excludeOid) {
        if (partIterationOid == null || linkType == null) return null;
        for (EngDocPartLink existing : linkMapper.selectByPartIterationOid(partIterationOid)) {
            if (excludeOid != null && excludeOid.equals(existing.getOid())) continue;
            if (linkType.equals(existing.getLinkType())) return existing;
        }
        return null;
    }

    /**
     * 同一零件迭代 + 同一关系类型只允许一条。
     * 2026-11-18：用户明确「每类只能一条」，故不再比较工程数据迭代——只要同类已有另一条就拒绝。
     */
    private void assertIterationLinkDuplicate(EngDocPartLink link, String excludeOid) {
        if (link.getLinkType() == null) return;
        EngDocPartLink dup = findByPartIterationAndType(
                link.getPartIterationOid(), link.getLinkType(), excludeOid);
        if (dup != null) {
            throw new IllegalArgumentException("该零件版本的「" + link.getLinkType()
                    + "」已关联一条记录，每类只能关联一条，请先解除或更换");
        }
    }

    /** 同一零件版本 + 同一份工程数据只允许一条规则 */
    private void assertNoDuplicate(String partIterationOid, String engDocumentOid, String excludeOid) {
        for (EngDocPartLink existing : linkMapper.selectByPartIterationOid(partIterationOid)) {
            if (existing.getOid().equals(excludeOid)) continue;
            if (engDocumentOid.equals(existing.getEngDocumentOid())) {
                throw new IllegalArgumentException("该零件版本已关联此工程数据，请直接修改关联类型");
            }
        }
    }

    /** OWNER / CONTRIBUTING_IMAGE 每个零件版本至多一条（EPMBuildRule 基数约束） */
    private void assertSingletonAssocType(String partIterationOid, String assocType, String excludeOid) {
        if (!EngDocPartLink.ASSOC_OWNER.equals(assocType)
                && !EngDocPartLink.ASSOC_CONTRIBUTING_IMAGE.equals(assocType)) {
            return;
        }
        for (EngDocPartLink existing : linkMapper.selectByPartIterationOid(partIterationOid)) {
            if (existing.getOid().equals(excludeOid)) continue;
            if (assocType.equals(existing.getAssocType())) {
                throw new IllegalArgumentException(
                        ("OWNER".equals(assocType) ? "主数据" : "贡献图纸") + "关联每个零件版本只能有一条，请先解除原有关系");
            }
        }
    }
}
