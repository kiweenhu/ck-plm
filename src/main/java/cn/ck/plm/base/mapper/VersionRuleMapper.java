/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.VersionRule;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 版本规则 Mapper 接口
 */
public interface VersionRuleMapper {

    /**
     * 插入版本规则
     */
    int insert(VersionRule rule);

    /**
     * 更新版本规则
     */
    int update(VersionRule rule);

    /**
     * 删除版本规则
     */
    int deleteByOid(@Param("oid") String oid);

    /**
     * 根据 OID 查询
     */
    VersionRule selectByOid(@Param("oid") String oid);

    /**
     * 根据 Code 查询
     */
    VersionRule selectByCode(@Param("code") String code);

    /**
     * 查询所有规则
     */
    List<VersionRule> selectAll();

    /**
     * 统计规则数量
     */
    int count();

    /**
     * 自增序号并返回新值
     */
    Long incrementAndGetSequence(@Param("code") String code);

    /**
     * 检查 Code 是否存在
     */
    int existsByCode(@Param("code") String code);
}
