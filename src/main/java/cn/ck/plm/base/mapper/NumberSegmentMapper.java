/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.NumberSegment;

import java.util.List;

/**
 * 编码规则段数据访问接口。
 *
 * <p>管理 {@code ck_number_segment} 表的 CRUD 及流水号原子递增。
 */
public interface NumberSegmentMapper {

    int insert(NumberSegment segment);

    int batchInsert(List<NumberSegment> segments);

    int update(NumberSegment segment);

    int deleteByRuleCode(String ruleCode);

    int deleteByOid(String oid);

    NumberSegment selectByOid(String oid);

    List<NumberSegment> selectByRuleCode(String ruleCode);

    /**
     * 原子递增指定 SERIAL 段的当前流水号并返回新值。
     *
     * @param oid SERIAL 段的 oid
     * @return 递增后的当前流水号
     */
    int incrementCurrentValue(String oid);

    /**
     * 重置指定 SERIAL 段的流水号为起始值。
     *
     * @param oid SERIAL 段的 oid
     * @param startValue 起始值
     */
    int resetCurrentValue(String oid, int startValue);
}
