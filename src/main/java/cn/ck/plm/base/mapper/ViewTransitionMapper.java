/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.ViewTransition;

import java.util.List;

/**
 * 视图切换规则数据访问接口。
 *
 * <p>定义与具体数据库无关的持久化契约，由 {@code mapper.impl} 包下的子接口
 * 对接不同数据库方言。当前实现：
 * <ul>
 *     <li>{@code mapper.impl.PostgreSqlViewTransitionMapper} — PostgreSQL</li>
 * </ul>
 */
public interface ViewTransitionMapper {

    int insert(ViewTransition transition);

    int update(ViewTransition transition);

    int deleteByOid(String oid);

    ViewTransition selectByOid(String oid);

    /** 查询从指定视图出发的所有规则（含禁用） */
    List<ViewTransition> selectByFromViewCode(String fromViewCode);

    /** 查询从指定视图出发的已启用规则 */
    List<ViewTransition> selectEnabledByFromViewCode(String fromViewCode);

    /** 查询从指定视图到目标视图的规则 */
    ViewTransition selectByFromAndTo(String fromViewCode, String toViewCode);

    List<ViewTransition> selectAll();

    int deleteByFromViewCode(String fromViewCode);

    int existsByFromAndTo(String fromViewCode, String toViewCode);
}
