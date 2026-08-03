/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.LifecycleTemplateDef;

/**
 * 子版本对象服务契约（IterationService），参考 Windchill 版本控制服务模式。
 *
 * <p>与实体解耦的独立服务层，提供检出/检入、生命周期管理等版本控制能力。
 * Entity 本身不实现本接口——调用方通过 Service 层操作实体。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * IterationService (this)       ←  版本控制 + 生命周期管理服务
 *   ↑ implements
 * IterationServiceImpl          ←  通用实现
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * IterationServiceImpl iterService = new IterationServiceImpl();
 * DocumentIteration iter = ...;
 *
 * // 检出
 * if (!iter.isCheckedOut()) {
 *     iterService.checkOut(iter, "zhangsan", "修改BOM");
 * }
 * // 检入
 * iterService.checkIn(iter);
 *
 * // 生命周期推进
 * if (iterService.canPromote(iter, LifecycleTemplateDef.STANDARD)) {
 *     iterService.promoteLifecycle(iter, LifecycleTemplateDef.STANDARD);
 * }
 * }</pre>
 */
public interface IterationService {

    // ==================== 检出/检入 ====================

    /** 检出，锁定当前版本防止并发修改 */
    void checkOut(IterationEntity iteration, String user, String comment);

    /** 检入，解除锁定并迭代号+1 */
    void checkIn(IterationEntity iteration);

    /** 撤销检出，放弃本次检出不增加迭代号 */
    void undoCheckOut(IterationEntity iteration);

    // ==================== 版本升级 ====================

    /**
     * 升级大版本，revision 递增一位，iteration 重置为 1。
     * 使用 char+1 简单递增（不回查版本规则）。
     */
    void newRevision(IterationEntity iteration);

    /**
     * 升级大版本（按版本规则）。
     *
     * @param iteration 当前 Iteration
     * @param ruleCode  版本规则编码，null 时回退到 char+1 简单递增
     */
    void newRevision(IterationEntity iteration, String ruleCode);

    // ==================== 副本追踪 ====================

    /** 标记目标版本为 source 的副本，自动重置检出状态 */
    void copyFrom(IterationEntity target, IterationEntity source);

    // ==================== 生命周期 ====================

    /** 推进生命周期到下一个状态 */
    void promoteLifecycle(IterationEntity iteration, LifecycleTemplateDef template);

    /** 回退生命周期到上一个状态 */
    void reject(IterationEntity iteration, LifecycleTemplateDef template);

    // ==================== 生命周期查询 ====================

    /** 查询是否可以向前推进 */
    boolean canPromote(IterationEntity iteration, LifecycleTemplateDef template);

    /** 查询是否可以回退 */
    boolean canReject(IterationEntity iteration, LifecycleTemplateDef template);

    // ==================== 数据操作 ====================

    /**
     * 从源子版本拷贝标准字段到目标（revision / iteration / latest / view / status）。
     * 子类服务应重写以同步拷贝自身专属字段。
     */
    void updateFrom(IterationEntity target, IterationEntity source);

    // ==================== 状态判断 ====================

    /** 判断是否被指定用户检出 */
    boolean isCheckedOutBy(IterationEntity iteration, String user);

    /** 判断是否为副本 */
    boolean isDerived(IterationEntity iteration);
}
