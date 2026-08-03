/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.ViewTransition;
import cn.ck.plm.base.mapper.ViewMapper;
import cn.ck.plm.base.mapper.ViewTransitionMapper;
import cn.ck.plm.base.service.api.ViewTransitionService;
import cn.ck.plm.base.util.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@link ViewTransitionService} 的 PostgreSQL 数据库实现。
 */
@Service
public class DefaultViewTransitionService implements ViewTransitionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultViewTransitionService.class);

    private final ViewTransitionMapper transitionMapper;
    private final ViewMapper viewMapper;

    public DefaultViewTransitionService(ViewTransitionMapper transitionMapper, ViewMapper viewMapper) {
        this.transitionMapper = transitionMapper;
        this.viewMapper = viewMapper;
    }

    @Override
    @Transactional
    public ViewTransition create(ViewTransition transition) {
        if (transition == null || transition.getFromViewCode() == null || transition.getToViewCode() == null) {
            throw new IllegalArgumentException("源视图和目标视图编码不能为空");
        }
        String fromCode = transition.getFromViewCode().trim();
        String toCode = transition.getToViewCode().trim();
        if (viewMapper.existsByCode(fromCode) == 0) {
            throw new IllegalArgumentException("源视图编码 '" + fromCode + "' 不存在");
        }
        if (viewMapper.existsByCode(toCode) == 0) {
            throw new IllegalArgumentException("目标视图编码 '" + toCode + "' 不存在");
        }
        if (transitionMapper.existsByFromAndTo(fromCode, toCode) > 0) {
            throw new IllegalArgumentException("切换规则 '" + fromCode + " → " + toCode + "' 已存在");
        }
        transition.setFromViewCode(fromCode);
        transition.setToViewCode(toCode);
        transition.setOid(UUID.randomUUID().toString());
        transition.setCreatedAt(LocalDateTime.now());
        transition.setUpdatedAt(LocalDateTime.now());
        transitionMapper.insert(transition);
        log.info("视图切换规则已创建: {} → {}", fromCode, toCode);
        return transition;
    }

    @Override
    @Transactional
    public ViewTransition update(ViewTransition transition) {
        if (transition == null || transition.getOid() == null) {
            throw new IllegalArgumentException("规则 oid 不能为空");
        }
        ViewTransition existing = transitionMapper.selectByOid(transition.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("规则不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "视图切换规则");
        transition.setUpdatedAt(LocalDateTime.now());
        transitionMapper.update(transition);
        log.info("视图切换规则已更新: oid={}", transition.getOid());
        return findByOid(transition.getOid());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) return false;
        ViewTransition existing = transitionMapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "视图切换规则");
        transitionMapper.deleteByOid(oid);
        log.info("视图切换规则已删除: oid={}", oid);
        return true;
    }

    @Override
    public ViewTransition findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) return null;
        return transitionMapper.selectByOid(oid.trim());
    }

    @Override
    public List<ViewTransition> findEnabledByFromViewCode(String fromViewCode) {
        if (fromViewCode == null || fromViewCode.trim().isEmpty()) return Collections.emptyList();
        return transitionMapper.selectEnabledByFromViewCode(fromViewCode.trim());
    }

    @Override
    public List<ViewTransition> findByFromViewCode(String fromViewCode) {
        if (fromViewCode == null || fromViewCode.trim().isEmpty()) return Collections.emptyList();
        return transitionMapper.selectByFromViewCode(fromViewCode.trim());
    }

    @Override
    public List<ViewTransition> findAll() {
        return transitionMapper.selectAll();
    }

    @Override
    public boolean exists(String fromViewCode, String toViewCode) {
        return fromViewCode != null && toViewCode != null
                && transitionMapper.existsByFromAndTo(fromViewCode.trim(), toViewCode.trim()) > 0;
    }
}
