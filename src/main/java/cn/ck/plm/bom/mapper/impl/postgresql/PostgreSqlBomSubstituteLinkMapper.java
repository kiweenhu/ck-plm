/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

import cn.ck.plm.bom.dto.BomSubstituteVO;
import cn.ck.plm.bom.entity.BomSubstituteLink;
import cn.ck.plm.bom.mapper.BomSubstituteLinkMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * 局部替代（{@code ck_bom_substitute_link}）的 PostgreSQL 实现。
 *
 * <h3>两条约束（踩过才知道疼，写在这里免得后人重踩）</h3>
 * <ol>
 *   <li><b>不写子查询</b>：{@code TenantStatementInterceptor} 靠正则往 SQL 里插租户条件，
 *       子查询里的 {@code LIMIT} / {@code ORDER BY} 会被当成本层关键字，插进去语法就废了。
 *       因此「替代件最新版本」这类信息不放这里取。</li>
 *   <li><b>别名用带双引号的驼峰</b>：表里是下划线列名、对象里是驼峰字段；
 *       仓库里已有结论 —— Map 返回时 {@code mapUnderscoreToCamelCase} 只做"去下划线"，
 *       直接写 {@code AS substitutePartNumber} 会被 PostgreSQL 折成全小写。</li>
 * </ol>
 *
 * <p>{@code tenant_oid} 不在这里写：拦截器会给 INSERT 自动补列、给查询自动加条件。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlBomSubstituteLinkMapper extends BomSubstituteLinkMapper {

    String TABLE = "ck_bom_substitute_link";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, code, name, description, bom_link_oid, source_part_oid, "
            + "substitute_part_oid, substitute_type, substitute_quantity, substitute_unit, priority, enabled, "
            + "creator, created_at, updater, updated_at) VALUES ("
            + "#{oid}, #{code}, #{name}, #{description}, #{bomLinkOid}, #{sourcePartOid}, "
            + "#{substitutePartOid}, #{substituteType}, #{substituteQuantity}, #{substituteUnit}, "
            + "#{priority}, #{enabled}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(BomSubstituteLink link);

    @Override
    @Update("UPDATE " + TABLE + " SET substitute_type = #{substituteType}, "
            + "substitute_quantity = #{substituteQuantity}, substitute_unit = #{substituteUnit}, "
            + "priority = #{priority}, enabled = #{enabled}, description = #{description}, "
            + "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(BomSubstituteLink link);

    String VO_COLUMNS =
            "SELECT s.oid AS \"oid\", s.bom_link_oid AS \"bomLinkOid\", "
            + "s.source_part_oid AS \"sourcePartOid\", s.substitute_part_oid AS \"substitutePartOid\", "
            + "s.substitute_type AS \"substituteType\", s.substitute_quantity AS \"substituteQuantity\", "
            + "s.substitute_unit AS \"substituteUnit\", s.priority AS \"priority\", "
            + "s.enabled AS \"enabled\", s.description AS \"description\", "
            + "sp.number AS \"substitutePartNumber\", sp.name AS \"substitutePartName\", "
            + "sp.type_definition_code AS \"substitutePartType\", "
            + "p.number AS \"sourcePartNumber\", p.name AS \"sourcePartName\" "
            + "FROM ck_bom_substitute_link s "
            + "LEFT JOIN ck_part sp ON sp.oid = s.substitute_part_oid "
            + "LEFT JOIN ck_part p ON p.oid = s.source_part_oid ";

    @Override
    @Select(VO_COLUMNS + "WHERE s.bom_link_oid = #{bomLinkOid} "
            + "ORDER BY s.priority NULLS LAST, s.created_at")
    List<BomSubstituteVO> selectVoByBomLinkOid(@Param("bomLinkOid") String bomLinkOid);

    /**
     * 批量计数：一条 SQL 拿回整棵树里每行的替代件数量（总数 + 启用数）。
     *
     * <p>不用 {@code IN (…)} 的 foreach 动态标签，理由见接口注释；{@code GROUP BY} 不碍事 ——
     * 租户拦截器是在 {@code GROUP BY} 之前插入 {@code tenant_oid} 条件的
     * （{@code TenantStatementInterceptor#rewriteWhere}），本仓库已有多个 GROUP BY 查询先例。
     */
    @Override
    @Select("SELECT bom_link_oid AS \"bomLinkOid\", COUNT(*) AS \"substituteCount\", "
            + "SUM(CASE WHEN enabled THEN 1 ELSE 0 END) AS \"substituteEnabledCount\" "
            + "FROM " + TABLE + " WHERE bom_link_oid::text = ANY(string_to_array(#{bomLinkOids}, ',')) "
            + "GROUP BY bom_link_oid")
    List<Map<String, Object>> countByBomLinkOids(@Param("bomLinkOids") String bomLinkOids);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE bom_link_oid = #{bomLinkOid}")
    int deleteByBomLinkOid(@Param("bomLinkOid") String bomLinkOid);
}
