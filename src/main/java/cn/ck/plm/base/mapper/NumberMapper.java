/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.Number;

import java.util.List;

/**
 * 编码规则数据访问接口。
 *
 * <p>仅负责 {@code number} 主表的 CRUD，段的持久化由 {@link NumberSegmentMapper} 负责。
 */
public interface NumberMapper {

    int insert(Number number);

    int update(Number number);

    int deleteByCode(String code);

    Number selectByCode(String code);

    List<Number> selectAll();

    List<Number> search(String keyword);

    int existsByCode(String code);
}
