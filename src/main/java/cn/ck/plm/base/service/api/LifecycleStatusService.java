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
}
