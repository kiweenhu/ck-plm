/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.LifecycleStatus;

import java.util.List;

/**
 * 生命周期状态数据访问接口。
 *
 * <p>定义与具体数据库无关的持久化契约，由 {@code mapper.impl} 包下的子接口或实现类
 * 对接不同数据库方言。当前实现：
 * <ul>
 *     <li>{@code mapper.impl.PostgreSqlLifecycleStatusMapper} — PostgreSQL</li>
 * </ul>
 */
public interface LifecycleStatusMapper {

    int insert(LifecycleStatus status);

    int update(LifecycleStatus status);

    int deleteByCode(String code);

    LifecycleStatus selectByCode(String code);

    List<LifecycleStatus> selectAll();

    List<LifecycleStatus> search(String keyword);

    int existsByCode(String code);

    /** 修复所有审计时间字段为 NULL 的记录（使用数据库 now() 函数） */
    int fixAllNullTimestamps();

    /** 修复 name/display_name 为 NULL 的存量记录（使用数据库 now() 函数） */
    int fixMissingDisplayName();
}
