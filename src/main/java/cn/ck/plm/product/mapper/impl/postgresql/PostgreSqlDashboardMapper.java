/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Dashboard;
import cn.ck.plm.product.mapper.DashboardMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link DashboardMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDashboardMapper extends DashboardMapper {

    String COLS = "oid, owner_oid, owner_type, code, name, description, audience, page_count, widgets, "
            + "generate_policy, primary_scope, industry_note, template_code, last_generated_at, "
            + "disabled, sort_order, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_dashboard (" + COLS + ") VALUES ("
            + "#{oid}, #{ownerOid}, #{ownerType}, #{code}, #{name}, #{description}, #{audience}, "
            + "#{pageCount}, #{widgets}, #{generatePolicy}, #{primaryScope}, #{industryNote}, "
            + "#{templateCode}, #{lastGeneratedAt}, #{disabled}, #{sortOrder}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Dashboard dashboard);

    @Override
    @Select("SELECT " + COLS + " FROM ck_dashboard WHERE oid = #{oid}")
    Dashboard selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM ck_dashboard WHERE owner_oid = #{ownerOid} "
            + "ORDER BY sort_order ASC, code ASC")
    List<Dashboard> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_dashboard WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_dashboard WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);
}
