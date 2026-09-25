/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.Number;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 重命名编码规则 code（数据修正用，幂等）。
     *
     * <p>不受 {@link #update(Number)} 限制——后者按 code 定位而无法改 code 本身。
     */
    int renameCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}
