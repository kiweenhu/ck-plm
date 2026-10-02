/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.GenPartThresholdConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 通用件阈值配置持久化（表 {@code ck_gen_part_threshold_config}，一租户一行）。
 *
 * <p>单文件注解式（同 {@code FileStorageConfigMapper}）：只有一条记录的配置不值得拆
 * 接口 + PostgreSQL 实现两层。列名走 {@code mapUnderscoreToCamelCase} 自动映射成驼峰，
 * 不需要逐列写 {@code @Results}。
 *
 * <p>{@code tenant_oid} 由租户拦截器按当前上下文注入/过滤；本类只按租户查一行。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface GenPartThresholdConfigMapper {

    String TABLE = "ck_gen_part_threshold_config";

    /** 当前租户的配置行（没有就返回 null，由 service 兜默认值） */
    @Select("SELECT * FROM " + TABLE + " WHERE tenant_oid = #{tenantOid} LIMIT 1")
    GenPartThresholdConfig selectByTenant(@Param("tenantOid") String tenantOid);

    @Insert("INSERT INTO " + TABLE + " (oid, enabled, min_model_count, min_usage_count, "
            + "stat_window_months, scope_type_code, process_template_oid, description, tenant_oid, "
            + "creator, created_at, updater, updated_at) VALUES ("
            + "#{oid}, #{enabled}, #{minModelCount}, #{minUsageCount}, "
            + "#{statWindowMonths}, #{scopeTypeCode}, #{processTemplateOid}, #{description}, #{tenantOid}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(GenPartThresholdConfig config);

    @Update("UPDATE " + TABLE + " SET enabled = #{enabled}, min_model_count = #{minModelCount}, "
            + "min_usage_count = #{minUsageCount}, stat_window_months = #{statWindowMonths}, "
            + "scope_type_code = #{scopeTypeCode}, process_template_oid = #{processTemplateOid}, "
            + "description = #{description}, updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(GenPartThresholdConfig config);
}
