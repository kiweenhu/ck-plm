/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
     * 序号 +1（返回影响行数；要拿新值请再调 {@link #selectSequenceValue(String)}）。
     *
     * <p>不要写成 {@code UPDATE … RETURNING sequence_value}：它走的是 executeUpdate，
     * MyBatis 拿不到 RETURNING 的值，返回的实际是「影响行数」—— 版本生成曾因此恒停在第 1 个字母。
     */
    int incrementSequence(@Param("code") String code);

    /** 读取当前序号（与 incrementSequence 在同一事务内调用即安全：UPDATE 已持有行锁） */
    Long selectSequenceValue(@Param("code") String code);

    /**
     * 检查 Code 是否存在
     */
    int existsByCode(@Param("code") String code);
}
