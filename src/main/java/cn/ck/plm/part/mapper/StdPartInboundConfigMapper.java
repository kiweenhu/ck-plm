/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.StdPartInboundConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 标准件入库流程配置持久化（表 {@code ck_std_part_inbound_config}，一租户一行）。
 *
 * <p>单文件注解式：只有一条记录的配置不值得拆接口 + PostgreSQL 实现两层
 * （同 {@code GenPartThresholdConfigMapper} / {@code FileStorageConfigMapper}）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface StdPartInboundConfigMapper {

    String TABLE = "ck_std_part_inbound_config";

    /** 当前租户的配置行（没有返回 null，由 service 兜默认值） */
    @Select("SELECT * FROM " + TABLE + " WHERE tenant_oid = #{tenantOid} LIMIT 1")
    StdPartInboundConfig selectByTenant(@Param("tenantOid") String tenantOid);

    @Insert("INSERT INTO " + TABLE + " (oid, enabled, process_template_oid, description, tenant_oid, "
            + "creator, created_at, updater, updated_at) VALUES ("
            + "#{oid}, #{enabled}, #{processTemplateOid}, #{description}, #{tenantOid}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(StdPartInboundConfig config);

    @Update("UPDATE " + TABLE + " SET enabled = #{enabled}, process_template_oid = #{processTemplateOid}, "
            + "description = #{description}, updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(StdPartInboundConfig config);
}
