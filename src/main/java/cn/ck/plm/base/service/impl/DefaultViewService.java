/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.mapper.ViewMapper;
import cn.ck.plm.base.mapper.ViewTransitionMapper;
import cn.ck.plm.base.service.api.ViewService;
import cn.ck.plm.base.util.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@link ViewService} 的 PostgreSQL 数据库实现。
 */
@Service
public class DefaultViewService implements ViewService {

    private static final Logger log = LoggerFactory.getLogger(DefaultViewService.class);

    private final ViewMapper viewMapper;
    private final ViewTransitionMapper transitionMapper;

    public DefaultViewService(ViewMapper viewMapper, ViewTransitionMapper transitionMapper) {
        this.viewMapper = viewMapper;
        this.transitionMapper = transitionMapper;
    }

    @Override
    @Transactional
    public View create(View view) {
        if (view == null || view.getCode() == null || view.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("视图编码不能为空");
        }
        String code = view.getCode().trim();
        if (viewMapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("视图编码 '" + code + "' 已存在");
        }
        view.setCode(code);
        view.setOid(UUID.randomUUID().toString());
        view.setCreatedAt(LocalDateTime.now());
        view.setUpdatedAt(LocalDateTime.now());
        viewMapper.insert(view);
        log.info("视图已创建: code={}, name={}", code, view.getName());
        return view;
    }

    @Override
    @Transactional
    public View update(View view) {
        if (view == null || view.getCode() == null || view.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("视图编码不能为空");
        }
        String code = view.getCode().trim();
        View existing = viewMapper.selectByCode(code);
        if (existing == null) {
            throw new IllegalArgumentException("视图编码 '" + code + "' 不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "视图");
        view.setOid(existing.getOid());
        view.setUpdatedAt(LocalDateTime.now());
        viewMapper.update(view);
        log.info("视图已更新: code={}", code);
        return findByCode(code);
    }

    @Override
    @Transactional
    public boolean delete(String code) {
        if (code == null || code.trim().isEmpty()) return false;
        String normalized = code.trim();
        View existing = viewMapper.selectByCode(normalized);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "视图");
        // 级联删除关联的切换规则
        transitionMapper.deleteByFromViewCode(normalized);
        viewMapper.deleteByCode(normalized);
        log.info("视图已删除: code={}", normalized);
        return true;
    }

    @Override
    public View findByCode(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        return viewMapper.selectByCode(code.trim());
    }

    @Override
    public List<View> findAllEnabled() {
        return viewMapper.selectAllEnabled();
    }

    @Override
    public List<View> findAll() {
        return viewMapper.selectAll();
    }

    @Override
    public List<View> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return findAll();
        return viewMapper.search(keyword.trim());
    }

    @Override
    public boolean exists(String code) {
        return code != null && viewMapper.existsByCode(code.trim()) > 0;
    }
}
