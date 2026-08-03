/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.entity.LifecycleTemplateDef;
import cn.ck.plm.base.service.IterationService;
import cn.ck.plm.base.service.api.VersionRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 子版本通用服务实现，参考 Windchill 的版本控制服务模式。
 *
 * <p>提供检出/检入、版本升级、副本标记、生命周期管理等标准能力。
 * 子类服务可重写 {@link #updateFrom(IterationEntity, IterationEntity)} 以同步拷贝专属字段。
 *
 * <h3>构造方式</h3>
 * <ul>
 *   <li>{@code new IterationServiceImpl()} — 无版本规则，revision 升级使用 char+1 兜底</li>
 *   <li>Spring 注入 — 自动注入 VersionRuleService，支持按规则序列跃迁</li>
 * </ul>
 */
@Service
public class IterationServiceImpl implements IterationService {

    private final VersionRuleService versionRuleService;

    /** 向后兼容的无参构造（无版本规则支持） */
    public IterationServiceImpl() {
        this.versionRuleService = null;
    }

    /** Spring 注入构造（可选启用版本规则支持） */
    @Autowired
    public IterationServiceImpl(VersionRuleService versionRuleService) {
        this.versionRuleService = versionRuleService;
    }

    // ==================== 检出/检入 ====================

    @Override
    public void checkOut(IterationEntity iteration, String user, String comment) {
        if (iteration.isCheckedOut()) {
            throw new IllegalStateException("版本已被 " + iteration.getCheckedOutBy() + " 检出");
        }
        iteration.setCheckedOut(true);
        iteration.setCheckedOutBy(user);
        iteration.setCheckedOutComment(comment);
    }

    @Override
    public void checkIn(IterationEntity iteration) {
        if (!iteration.isCheckedOut()) {
            throw new IllegalStateException("版本未被检出，无法检入");
        }
        iteration.setCheckedOut(false);
        iteration.setCheckedOutBy(null);
        iteration.setCheckedOutComment(null);
        iteration.setIteration(iteration.getIteration() + 1);
    }

    @Override
    public void undoCheckOut(IterationEntity iteration) {
        iteration.setCheckedOut(false);
        iteration.setCheckedOutBy(null);
        iteration.setCheckedOutComment(null);
    }

    // ==================== 版本升级 ====================

    @Override
    public void newRevision(IterationEntity iteration) {
        newRevision(iteration, null);
    }

    /**
     * 大版本跃迁（new revision）。
     * <p>优先使用版本规则中的序列（如 A→B→C→D→E→F），
     * 若未绑规则则回退到 char+1 的简单递增。
     *
     * @param iteration 当前 Iteration
     * @param ruleCode  版本规则编码（可为 null，null 时回退 char+1）
     */
    @Override
    public void newRevision(IterationEntity iteration, String ruleCode) {
        String next = null;
        if (versionRuleService != null && ruleCode != null) {
            try {
                next = versionRuleService.getNextRevision(ruleCode, iteration.getRevision());
            } catch (Exception ignored) { /* 回退到 char+1 */ }
        }
        if (next == null) {
            char c = iteration.getRevision().charAt(0);
            next = String.valueOf((char) (c + 1));
        }
        iteration.setRevision(next);
        iteration.setIteration(1);
    }

    // ==================== 副本追踪 ====================

    @Override
    public void copyFrom(IterationEntity target, IterationEntity source) {
        target.setDerivedFromOid(source.getOid());
        target.setDerivedAt(java.time.LocalDateTime.now());
        target.setCheckedOut(false);
        target.setCheckedOutBy(null);
        target.setCheckedOutComment(null);
    }

    // ==================== 生命周期 ====================

    @Override
    public void promoteLifecycle(IterationEntity iteration, LifecycleTemplateDef template) {
        LifecycleStatus status = iteration.getStatus();
        if (status == null) {
            LifecycleStatus first = template.getStatuses().isEmpty()
                    ? null : template.getStatuses().get(0);
            iteration.setStatus(first);
        } else {
            LifecycleStatus next = template.promote(status.getCode());
            if (next != null) {
                iteration.setStatus(next);
            }
        }
    }

    @Override
    public void reject(IterationEntity iteration, LifecycleTemplateDef template) {
        LifecycleStatus status = iteration.getStatus();
        if (status == null) return;
        LifecycleStatus prev = template.reject(status.getCode());
        if (prev != null) {
            iteration.setStatus(prev);
        }
    }

    // ==================== 生命周期查询 ====================

    @Override
    public boolean canPromote(IterationEntity iteration, LifecycleTemplateDef template) {
        if (template == null) return false;
        LifecycleStatus status = iteration.getStatus();
        if (status == null) return !template.getStatuses().isEmpty();
        return template.promote(status.getCode()) != null;
    }

    @Override
    public boolean canReject(IterationEntity iteration, LifecycleTemplateDef template) {
        if (template == null) return false;
        LifecycleStatus status = iteration.getStatus();
        if (status == null) return false;
        return template.reject(status.getCode()) != null;
    }

    // ==================== 数据操作 ====================

    @Override
    public void updateFrom(IterationEntity target, IterationEntity source) {
        target.setRevision(source.getRevision());
        target.setIteration(source.getIteration());
        target.setLatest(source.isLatest());
        target.setView(source.getView());
        target.setStatus(source.getStatus());
    }

    // ==================== 状态判断 ====================

    @Override
    public boolean isCheckedOutBy(IterationEntity iteration, String user) {
        return iteration.isCheckedOut()
                && user != null
                && user.equals(iteration.getCheckedOutBy());
    }

    @Override
    public boolean isDerived(IterationEntity iteration) {
        return iteration.getDerivedFromOid() != null;
    }
}
