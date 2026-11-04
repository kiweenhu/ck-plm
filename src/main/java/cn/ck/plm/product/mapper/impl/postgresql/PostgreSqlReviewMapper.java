/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Review;
import cn.ck.plm.product.mapper.ReviewMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ReviewMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlReviewMapper extends ReviewMapper {

    String COLS = "oid, owner_oid, owner_type, code, name, description, stage_key, gate, type, process_key, "
            + "check_items, output_types, verdict_options, industry_note, template_code, status, "
            + "verdict, verdict_note, reviewed_at, disabled, sort_order, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_review (" + COLS + ") VALUES ("
            + "#{oid}, #{ownerOid}, #{ownerType}, #{code}, #{name}, #{description}, #{stageKey}, #{gate}, #{type}, "
            + "#{processKey}, #{checkItems}, #{outputTypes}, #{verdictOptions}, #{industryNote}, "
            + "#{templateCode}, #{status}, #{verdict}, #{verdictNote}, #{reviewedAt}, #{disabled}, #{sortOrder}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Review review);

    @Override
    @Select("SELECT " + COLS + " FROM ck_review WHERE oid = #{oid}")
    Review selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM ck_review WHERE owner_oid = #{ownerOid} "
            + "ORDER BY stage_key ASC, sort_order ASC, code ASC")
    List<Review> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_review WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_review WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Update("UPDATE ck_review SET disabled = #{disabled}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateDisabled(@Param("oid") String oid, @Param("disabled") boolean disabled);
}
