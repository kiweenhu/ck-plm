/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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
     * （配置界面从状态清单里选，如 IN_WORK / PUBLISHED），而引擎侧原本只有
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
     * <p><b>逐跳沿模板的回退规则走</b>，而不是一步跳过去：多跳的情况（RELEASED→DRAFT）在模板里
     * 本来就没有直接规则，允许"跳跃"等于把回退链当成摆设 —— 那正是生命周期模板要防的事。
     * 某一跳没有回退规则就明确失败（说清卡在哪个状态），而不是悄悄改掉状态。
     *
     * <p>只改内存对象，不落库（各宿主自己持久化，与 {@link #moveToState} 一致）。
     *
     * @throws IllegalArgumentException 模板里没有回退规则（走不到初始状态）
     * @throws IllegalStateException    未绑定模板 / 回退规则成环
     */
    void moveToInitialState(IterationEntity iteration);
}
