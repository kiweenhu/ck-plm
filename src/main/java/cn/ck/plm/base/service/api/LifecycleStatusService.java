/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.LifecycleStatus;

import java.util.List;

/**
 * 生命周期状态服务接口，定义生命周期状态的 CRUD 操作规范。
 *
 * <p>职责：
 * <ul>
 *   <li>创建新的生命周期状态</li>
 *   <li>编辑已有生命周期状态（编码不可变更）</li>
 *   <li>删除生命周期状态</li>
 *   <li>查阅生命周期状态（按编码查找、列表查询）</li>
 * </ul>
 *
 * <p>所有实现类需保证：
 * <ul>
 *   <li>code 全局唯一，不可重复</li>
 *   <li>code 全局唯一，不可重复</li>
 * </ul>
 */
public interface LifecycleStatusService {

    /**
     * 创建新的生命周期状态。
     *
     * @param status 待创建的状态对象
     * @return 创建成功后的状态对象
     * @throws IllegalArgumentException 如果 code 已存在或必填字段为空
     */
    LifecycleStatus create(LifecycleStatus status);

    /**
     * 编辑已有生命周期状态。
     * 仅允许修改 name，code 不可变更。
     *
     * @param status 包含更新后字段的状态对象（以 code 为标识）
     * @return 更新后的状态对象
     * @throws IllegalArgumentException 如果 code 对应的状态不存在
     */
    LifecycleStatus update(LifecycleStatus status);

    /**
     * 根据状态编码删除生命周期状态。
     *
     * @param code 状态编码
     * @return true 删除成功，false 状态不存在
     * @throws IllegalStateException 如果该状态被模板引用无法删除
     */
    boolean delete(String code);

    /**
     * 根据状态编码查阅生命周期状态。
     *
     * @param code 状态编码
     * @return 对应的生命周期状态，未找到返回 null
     */
    LifecycleStatus findByCode(String code);

    /**
     * 查阅所有生命周期状态，按 code 升序排列。
     *
     * @return 所有生命周期状态列表
     */
    List<LifecycleStatus> findAll();

    /**
     * 根据编码前缀模糊查询状态列表。
     *
     * @param keyword 编码或名称关键字
     * @return 匹配的状态列表
     */
    List<LifecycleStatus> search(String keyword);

    /**
     * 判断指定编码的状态是否已存在。
     *
     * @param code 状态编码
     * @return true 已存在
     */
    boolean exists(String code);

    // ==================== code → 显示名 ====================

    /**
     * 状态 code → 显示名（人看的那个字：{@code DRAFT} → 「草稿」）。
     *
     * <p><b>解析顺序</b>：该对象所绑<b>生命周期模板</b>里的显示名 → 全局状态字典 → code 本身。
     *
     * <p>为什么模板在前：迭代上存的 {@code status} 是 code（见 LifecycleStatusTypeHandler），
     * 而"这个 code 叫什么"的权威定义就在它所用的模板里（{@code ck_lifecycle_template_state}
     * 的 code 与 display_name 是配套写下的，天然对得上）。全局字典是另一层东西，
     * 各租户的状态词未必一致、也可能压根没有该 code —— 只认字典的结果就是界面上直接显示 code，
     * 而用户并不知道 {@code IN_WORK} 就是"工作中"。
     *
     * <p>不缓存：模板改完要立刻生效（改完状态名却还显示旧的，比慢一点更难解释）。
     *
     * @param templateIterationOid 迭代绑定的生命周期模板子版本 oid（可为空）
     * @param code                 迭代上的状态 code
     * @return 显示名；无任何来源时返回 code 本身（宁可显示 code，也不显示空白）
     */
    String displayName(String templateIterationOid, String code);

    /**
     * 某模板子版本下 code → 显示名 的整表（列表页一次取全，避免逐行查询）。
     *
     * @param templateIterationOid 模板子版本 oid；为空返回空表
     * @return 状态 code → 显示名（缺失显示名的以 code 兜底）
     */
    java.util.Map<String, String> displayNames(String templateIterationOid);
}
