/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.integration.entity.TargetSystem;
import cn.ck.plm.integration.mapper.TargetSystemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 目标系统注册表服务 —— 管理员维护 + 运行期按编码取用。
 */
@Service
public class TargetSystemService {

    /** 未指明认证方式的默认值：不认证（内网直连最常见） */
    private static final String AUTH_NONE = "NONE";

    private final TargetSystemMapper mapper;

    public TargetSystemService(TargetSystemMapper mapper) {
        this.mapper = mapper;
    }

    /** 列表：平台管理员看全部，租户管理员看本租户 */
    public List<TargetSystem> listAll() {
        return TenantContext.isCurrentPlatform() ? mapper.selectAll() : mapper.selectByTenant(TenantContext.get());
    }

    public TargetSystem getByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    /**
     * 运行期按编码取系统（出站调用用）。
     *
     * <p>租户参数由调用方给（流程实例所属租户），不依赖线程上下文 ——
     * 出站调用可能发生在异步作业线程里，"当前租户"未必等于"流程实例的租户"。
     */
    public TargetSystem findByCode(String code, String tenantOid) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return mapper.selectByCodeAndTenant(code, tenantOid);
    }

    @Transactional
    public TargetSystem create(TargetSystem system) {
        system.setOid(UUID.randomUUID().toString());
        if (system.getTenantOid() == null) {
            system.setTenantOid(TenantContext.get());
        }
        String user = UserContext.get();
        LocalDateTime now = LocalDateTime.now();
        system.setCreator(user);
        system.setCreatedAt(now);
        system.setUpdater(user);
        system.setUpdatedAt(now);
        normalize(system);
        mapper.insert(system);
        return system;
    }

    /**
     * 更新。
     *
     * <p><b>凭据留空表示不修改</b>：接口从不回传明文密钥（避免它出现在浏览器控制台、
     * 前端缓存、日志里），所以前端编辑时那一栏是空的 —— 若把空值直接写库，
     * 用户"只想改个地址"就会把凭据清掉，且直到运行期调用失败才发现。
     */
    @Transactional
    public TargetSystem update(TargetSystem system) {
        TargetSystem existing = mapper.selectByOid(system.getOid());
        if (existing == null) {
            return null;
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "目标系统");
        if (system.getSecret() == null || system.getSecret().isEmpty()) {
            system.setSecret(existing.getSecret());
        }
        if (system.getTenantOid() == null) {
            system.setTenantOid(existing.getTenantOid());
        }
        system.setUpdater(UserContext.get());
        system.setUpdatedAt(LocalDateTime.now());
        normalize(system);
        mapper.update(system);
        return system;
    }

    @Transactional
    public boolean delete(String oid) {
        TargetSystem existing = mapper.selectByOid(oid);
        if (existing == null) {
            return false;
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "目标系统");
        mapper.deleteByOid(oid);
        return true;
    }

    /** 缺省值兜底：认证方式为空按 NONE，启用/排序给默认，编码去空格（节点上按它引用） */
    private void normalize(TargetSystem system) {
        if (system.getAuthType() == null || system.getAuthType().isEmpty()) {
            system.setAuthType(AUTH_NONE);
        }
        if (system.getEnabled() == null) {
            system.setEnabled(Boolean.TRUE);
        }
        if (system.getSortOrder() == null) {
            system.setSortOrder(0);
        }
        if (system.getCode() != null) {
            system.setCode(system.getCode().trim());
        }
    }
}
