/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Metric;
import cn.ck.plm.product.mapper.MetricMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link MetricMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlMetricMapper extends MetricMapper {

    String COLS = "oid, owner_oid, owner_type, code, name, description, layer, level, source, "
            + "industry_note, template_code, value, value_text, readiness, evaluated_at, "
            + "disabled, sort_order, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_metric (" + COLS + ") VALUES ("
            + "#{oid}, #{ownerOid}, #{ownerType}, #{code}, #{name}, #{description}, #{layer}, #{level}, "
            + "#{source}, #{industryNote}, #{templateCode}, #{value}, #{valueText}, #{readiness}, "
            + "#{evaluatedAt}, #{disabled}, #{sortOrder}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Metric metric);

    @Override
    @Select("SELECT " + COLS + " FROM ck_metric WHERE oid = #{oid}")
    Metric selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM ck_metric WHERE owner_oid = #{ownerOid} "
            + "ORDER BY layer ASC, sort_order ASC, code ASC")
    List<Metric> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_metric WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_metric WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Update("UPDATE ck_metric SET value = #{value}, value_text = #{valueText}, readiness = #{readiness}, "
            + "evaluated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateEvaluation(@Param("oid") String oid,
                         @Param("value") BigDecimal value,
                         @Param("valueText") String valueText,
                         @Param("readiness") String readiness);
}
