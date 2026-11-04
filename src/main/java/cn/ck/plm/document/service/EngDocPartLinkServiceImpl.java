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
import cn.ck.plm.document.mapper.EngDocPartLinkMapper;
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

    public EngDocPartLinkServiceImpl(EngDocPartLinkMapper linkMapper) {
        this.linkMapper = linkMapper;
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
        assertPartIterationSingleton(link.getPartIterationOid());
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
        existing.setEngDocIterationOid(link.getEngDocIterationOid());
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

    /**
     * 一个零件版本**有且只有一份**工程数据（2026-10-26 用户口径）。
     *
     * <p>原来只按关联类型限（OWNER / CONTRIBUTING_IMAGE 各一份），CONTENT 可以挂任意多份 ——
     * 结果是同一个零件版本能挂上一堆工程数据，CAD 描述 / 数模到底以哪份为准全靠猜。
     * 现在整体只允许一条：要换对象先解除原有关系。
     *
     * <p>服务层先拦（给可读提示），数据库侧 {@code uk_engpl_part_iter} 唯一索引兜并发。
     */
    private void assertPartIterationSingleton(String partIterationOid) {
        List<EngDocPartLink> existing = linkMapper.selectByPartIterationOid(partIterationOid);
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException(
                    "该零件版本已关联工程数据（一个零件版本只能关联一份），请先解除原有关系再关联");
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
