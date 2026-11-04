/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.service.MasterService;

import java.util.List;

/**
 * 生命周期模板服务接口，扩展 MasterService 的版本控制能力。
 */
public interface LifecycleTemplateService extends MasterService {

    /** 创建模板（含初始子版本、状态关联和流转规则） */
    LifecycleTemplateMaster create(LifecycleTemplateMaster template);

    /** 更新模板（全量替换状态和流转规则） */
    LifecycleTemplateMaster update(LifecycleTemplateMaster template);

    /** 删除模板（级联删除子版本、状态关联和流转规则） */
    boolean delete(String code);

    /** 按编码查询（含 states、transitions、rejections、latestIteration） */
    LifecycleTemplateMaster findByCode(String code);

    /**
     * 按模板<b>子版本 oid</b> 反查模板（业务对象迭代里固化的就是子版本 oid）。
     *
     * <p>与 {@link #findByCode} 的区别：states / transitions / rejections 取的是<b>该子版本</b>的，
     * 而不是最新版的 —— 对象"出生"时固化的是哪一版，执行侧（{@code moveToState}）就按哪一版判，
     * 候选也必须来自同一版，否则改过模板之后旧对象会出现"界面让选、后端拒收"。
     *
     * @return 模板（{@code latestIteration} 位置放的就是传入的那一版）；查不到返回 null
     */
    LifecycleTemplateMaster findByIterationOid(String templateIterationOid);

    /**
     * 把迭代<b>重新绑定</b>到目标模板子版本，并把当前状态按 code 映射过去：
     * 新模板里有同 code 的状态就保留，没有就落到新模板的初始状态。
     *
     * <p>只改内存对象、不落库（与 {@link #moveToState} 一致，各宿主自己持久化）。
     *
     * @param iteration                 迭代实体
     * @param templateIterationOid      目标模板子版本 oid（{@code ck_lifecycle_template_iteration.oid}）
     * @return 迁移后的状态 code
     * @throws IllegalArgumentException 子版本不存在 / 目标模板没有可用状态
     */
    String rebindLifecycle(IterationEntity iteration, String templateIterationOid);

    /** 查询所有模板（含 states、transitions、rejections、latestIteration） */
    List<LifecycleTemplateMaster> findAll();

    /** 模糊搜索 */
    List<LifecycleTemplateMaster> search(String keyword);

    /** 判断编码是否已存在 */
    boolean exists(String code);

    /**
     * 根据 typeDefinitionCode 查找绑定的生命周期模板，初始化迭代记录的生命周期状态。
     * 设置迭代的 lifecycleTemplateIterationOid 和初始 status。
     *
     * @param iter     迭代实体
     * @param typeCode 类型定义编码
     */
    void initLifecycle(IterationEntity iter, String typeCode);

    /**
     * 把迭代迁到<b>指定状态</b>（相对 promote/reject 的"向前一格 / 向后一格"，这是"去哪个状态"）。
     *
     * <p><b>只改内存对象，不落库</b>：调用方自己决定怎么持久化（各宿主有各自的迭代表与
     * 更新时间字段）。这样本方法对 Part / Document / EngineeringDocument 都成立。
     *
     * <p>为什么要有它：流程里的「设置状态」服务节点给的是<b>目标状态 code</b>
     * （配置界面从状态清单里选，如 WORKING / PUBLISHED），而引擎侧原本只有
     * {@code promoteLifecycle}（按模板迁移表往前一格）—— 两者不是一回事：
     * 用户配的是"到哪个状态"，模板决定的是"从当前状态能不能到那儿"。
     *
     * @param iteration       迭代实体（须已绑定模板子版本，否则无法校验迁移规则）
     * @param targetStateCode 目标状态 code
     * @throws IllegalArgumentException 目标状态不在该模板内，或当前状态不允许迁到它
     */
    void moveToState(IterationEntity iteration, String targetStateCode);

    /**
     * 把迭代退回<b>模板的初始状态</b>（"重置回起点"，如 已发布 → 工作中 → 草稿）。
     *
     * <p><b>逐跳沿模板的回退规则走</b>，而不是一步跳过去：多跳的情况（PUBLISHED→DRAFT）在模板里
     * 本来就没有直接规则，允许"跳跃"等于把回退链当成摆设 —— 那正是生命周期模板要防的事。
     * 某一跳没有回退规则就明确失败（说清卡在哪个状态），而不是悄悄改掉状态。
     *
     * <p>只改内存对象，不落库（各宿主自己持久化，与 {@link #moveToState} 一致）。
     *
     * @throws IllegalArgumentException 模板里没有回退规则（走不到初始状态）
     * @throws IllegalStateException    未绑定模板 / 回退规则成环
     */
    void moveToInitialState(IterationEntity iteration);

    /**
     * 修订前置校验：对象<b>当前状态</b>在它固化的模板版本里是否被标记为「可修订」。
     *
     * <p>「是否可修订」是模板状态的属性（{@code ck_lifecycle_template_state.revisable}），
     * 不是类型或全局配置：同一个状态在不同模板里可以有不同的业务含义，
     * 而模板会被多个类型复用，按类型配置就得为每个类型重复一份，必然漂移。
     *
     * <p><b>配置缺失不阻断</b>（与 {@code RevisionRuleGuard} 同口径）：没有绑定模板、
     * 没有状态、状态已不在模板内、模板读取失败 —— 都放行，让调用方去报它自己真正的问题。
     * 只有明确配成 {@code revisable=false} 才拒绝。
     *
     * @param iteration 迭代实体（须含 status 与 lifecycleTemplateIterationOid）
     * @throws IllegalStateException 当前状态不允许修订
     */
    void ensureRevisable(IterationEntity iteration);

    /**
     * 设置某状态是否允许修订 —— 由<b>租户管理员</b>在自己的「业务配置 → 生命周期」里决定。
     *
     * <p>与模板结构编辑分开：这是一项权限开关，不产生新的模板子版本。改动会写到该模板的
     * <b>全部子版本</b>上 —— 在途对象固化的是旧子版本，只改最新版就会出现"配置改了却不生效"。
     *
     * <p>权限：平台模板只有平台管理员能改；租户模板由本租户管理员改
     * （见 {@code TenantContext#requireEditPermission}）。
     *
     * @param templateCode 模板编码
     * @param statusCode   状态编码（须在该模板内）
     * @param revisable    true=允许修订
     * @throws IllegalArgumentException 模板/状态不存在
     * @throws IllegalStateException    无权编辑该模板
     */
    void setStateRevisable(String templateCode, String statusCode, boolean revisable);

    /**
     * 批量解析某模板子版本下各状态的可修订标记（statusCode → revisable）。
     *
     * <p>列表页要给每一行算「能不能修订」，逐行查模板会打出 N+1；这里一次取完，
     * 调用方按 {@code templateIterationOid} 缓存即可。
     *
     * <p>模板不存在 / 没有状态定义 / 读取失败都返回空 Map，调用方按"允许"处理
     * （与 {@link #ensureRevisable} 的"配置缺失不阻断"一致）。
     */
    java.util.Map<String, Boolean> revisableFlags(String templateIterationOid);

    /**
     * 取某模板子版本的<b>初始状态</b>（主档配置的 initialStateCode，缺失时取状态列表第一个）。
     *
     * <p>修订（新大版本）要"重新初始化生命周期"，必须用对象自己固化的那个模板子版本去解析，
     * 而不是当前业务配置的最新模板 —— 否则状态可能落在新模板里不存在的 code 上。
     *
     * @return 解析不到（未绑定模板 / 模板无状态 / 读取失败）返回 {@code null}
     */
    cn.ck.plm.base.entity.LifecycleStatus initialStatus(String templateIterationOid);
}
