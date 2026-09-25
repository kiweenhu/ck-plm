/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.process.entity.ProcessCategory;
import cn.ck.plm.process.mapper.ProcessCategoryMapper;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.service.api.ProcessCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link ProcessCategoryService} 默认实现。
 *
 * <p>三条硬规则：
 * <ol>
 *   <li><b>名称必填且租户内唯一</b> —— 名称只用于显示与避免重名，模板引用的是 oid；</li>
 *   <li><b>改名不动模板</b> —— 模板挂的是 oid，改显示名天然生效
 *       （从"名称引用"换成"oid 引用"之后，原先的"改名同步模板"逻辑整体消失了）；</li>
 *   <li><b>删除前检查占用</b> —— 组内还有流程时拒绝，避免模板被静默丢进「未分类」。</li>
 * </ol>
 */
@Service
public class ProcessCategoryServiceImpl implements ProcessCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ProcessCategoryServiceImpl.class);

    /** 分组名长度上限（与 DDL 的 VARCHAR(64) 一致，提前给出可读报错） */
    private static final int NAME_MAX = 64;

    private final ProcessCategoryMapper categoryMapper;
    private final ProcessTemplateMapper templateMapper;

    public ProcessCategoryServiceImpl(ProcessCategoryMapper categoryMapper,
                                      ProcessTemplateMapper templateMapper) {
        this.categoryMapper = categoryMapper;
        this.templateMapper = templateMapper;
    }

    @Override
    public List<ProcessCategory> list() {
        return categoryMapper.selectList();
    }

    @Override
    @Transactional
    public ProcessCategory create(String name, Integer sortOrder, String description) {
        String trimmed = requireName(name);
        if (categoryMapper.countByName(trimmed, null) > 0) {
            throw new IllegalArgumentException("分组已存在: " + trimmed);
        }
        ProcessCategory entity = new ProcessCategory();
        entity.setName(trimmed);
        entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
        entity.setDescription(trimToNull(description));
        entity.setTenantOid(TenantContext.get());
        entity.setCreator(UserContext.get());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdater(UserContext.get());
        entity.setUpdatedAt(LocalDateTime.now());
        categoryMapper.insert(entity);
        log.info("流程分组已创建: name={} oid={}", trimmed, entity.getOid());
        return entity;
    }

    @Override
    @Transactional
    public ProcessCategory update(String oid, String name, Integer sortOrder, String description) {
        ProcessCategory entity = requireByOid(oid);
        String oldName = entity.getName();
        String trimmed = requireName(name);
        if (!trimmed.equals(oldName) && categoryMapper.countByName(trimmed, oid) > 0) {
            throw new IllegalArgumentException("分组已存在: " + trimmed);
        }
        entity.setName(trimmed);
        entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
        entity.setDescription(trimToNull(description));
        entity.setUpdater(UserContext.get());
        entity.setUpdatedAt(LocalDateTime.now());
        categoryMapper.update(entity);
        // 模板引用的是 oid → 改名不必触碰任何模板行（这里刻意没有"同步模板"的代码）
        if (!trimmed.equals(oldName)) {
            log.info("流程分组已改名: {} → {}（模板按 oid 引用，无需同步）", oldName, trimmed);
        }
        return entity;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        ProcessCategory entity = requireByOid(oid);
        int used = templateMapper.countByCategory(entity.getOid());
        if (used > 0) {
            throw new IllegalArgumentException(
                    "分组「" + entity.getName() + "」下还有 " + used + " 个流程，请先移动或删除后再删分组");
        }
        categoryMapper.deleteByOid(oid);
        log.info("流程分组已删除: name={}", entity.getName());
    }

    @Override
    public ProcessCategory requireByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            throw new IllegalArgumentException("分组不能为空（请先在流程清单左侧选择或创建分组）");
        }
        ProcessCategory found = categoryMapper.selectByOid(oid.trim());
        if (found == null) {
            throw new IllegalArgumentException("分组不存在: " + oid + "（请刷新流程清单确认该分组是否已被删除）");
        }
        return found;
    }

    // ==================== 私有工具 ====================


    private static String requireName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new IllegalArgumentException("分组名不能为空");
        }
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("分组名不能超过 " + NAME_MAX + " 个字符");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
