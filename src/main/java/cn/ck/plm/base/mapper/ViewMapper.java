/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.View;

import java.util.List;

/**
 * 视图数据访问接口。
 *
 * <p>定义与具体数据库无关的持久化契约，由 {@code mapper.impl} 包下的子接口
 * 对接不同数据库方言。当前实现：
 * <ul>
 *     <li>{@code mapper.impl.PostgreSqlViewMapper} — PostgreSQL</li>
 * </ul>
 */
public interface ViewMapper {

    int insert(View view);

    int update(View view);

    int deleteByCode(String code);

    View selectByCode(String code);

    List<View> selectAllEnabled();

    List<View> selectAll();

    List<View> search(String keyword);

    int existsByCode(String code);
}
