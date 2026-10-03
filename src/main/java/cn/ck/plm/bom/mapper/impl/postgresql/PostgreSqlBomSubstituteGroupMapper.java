/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

import cn.ck.plm.bom.dto.BomSubstituteGroupVO;
import cn.ck.plm.bom.entity.BomSubstituteGroup;
import cn.ck.plm.bom.entity.BomSubstituteGroupMember;
import cn.ck.plm.bom.mapper.BomSubstituteGroupMapper;
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
 * 成组替代的 PostgreSQL 实现。
 *
 * <p>与局部替代同一套写法约束（不写子查询、别名带双引号驼峰、tenant_oid 交给拦截器），
 * 原因见 {@code PostgreSqlBomSubstituteLinkMapper} 的类注释，这里不再重复。
 *
 * <p>成员查询要 join 三张表才能凑出界面上要看的字：{@code ck_bom_links}（行号）、
 * 其子件 {@code ck_part}（原料侧编码/名称）、{@code ck_part}（替代侧编码/名称）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlBomSubstituteGroupMapper extends BomSubstituteGroupMapper {

    String GROUP_TABLE = "ck_bom_substitute_group";
    String MEMBER_TABLE = "ck_bom_substitute_group_member";

    String GROUP_COLUMNS =
            "SELECT g.oid AS \"oid\", g.code AS \"code\", g.name AS \"name\", "
            + "g.description AS \"description\", g.parent_iteration_oid AS \"parentIterationOid\", "
            + "g.status AS \"status\", g.atomic_replace AS \"atomicReplace\", g.enabled AS \"enabled\", "
            + "g.creator AS \"creator\", g.created_at AS \"createdAt\", "
            + "g.updater AS \"updater\", g.updated_at AS \"updatedAt\" "
            + "FROM ck_bom_substitute_group g ";

    @Override
    @Insert("INSERT INTO " + GROUP_TABLE + " (oid, code, name, description, parent_iteration_oid, status, "
            + "atomic_replace, enabled, creator, created_at, updater, updated_at) VALUES ("
            + "#{oid}, #{code}, #{name}, #{description}, #{parentIterationOid}, #{status}, "
            + "#{atomicReplace}, #{enabled}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insertGroup(BomSubstituteGroup group);

    @Override
    @Update("UPDATE " + GROUP_TABLE + " SET name = #{name}, description = #{description}, "
            + "status = #{status}, atomic_replace = #{atomicReplace}, enabled = #{enabled}, "
            + "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updateGroup(BomSubstituteGroup group);

    @Override
    @Delete("DELETE FROM " + GROUP_TABLE + " WHERE oid = #{oid}")
    int deleteGroupByOid(@Param("oid") String oid);

    @Override
    @Select(GROUP_COLUMNS + "WHERE g.oid = #{oid}")
    BomSubstituteGroupVO selectVoByOid(@Param("oid") String oid);

    @Override
    @Select(GROUP_COLUMNS + "WHERE g.parent_iteration_oid = #{parentIterationOid} ORDER BY g.created_at")
    List<BomSubstituteGroupVO> selectVoByParentIterationOid(@Param("parentIterationOid") String parentIterationOid);

    String MEMBER_COLUMNS =
            "SELECT m.oid AS \"oid\", m.group_oid AS \"groupOid\", m.member_side AS \"memberSide\", "
            + "m.bom_link_oid AS \"bomLinkOid\", m.part_oid AS \"partOid\", m.quantity AS \"quantity\", "
            + "m.unit AS \"unit\", m.sort_order AS \"sortOrder\", "
            + "l.line_number AS \"bomLineNumber\", "
            + "lp.number AS \"sourcePartNumber\", lp.name AS \"sourcePartName\", "
            + "p.number AS \"partNumber\", p.name AS \"partName\", p.type_definition_code AS \"partType\" "
            + "FROM ck_bom_substitute_group_member m "
            + "LEFT JOIN ck_bom_links l ON l.oid = m.bom_link_oid "
            + "LEFT JOIN ck_part lp ON lp.oid = l.child_part_oid "
            + "LEFT JOIN ck_part p ON p.oid = m.part_oid ";

    @Override
    @Select(MEMBER_COLUMNS + "WHERE m.group_oid::text = ANY(string_to_array(#{groupOids}, ',')) "
            + "ORDER BY m.group_oid, m.member_side, m.sort_order NULLS LAST, m.created_at")
    List<BomSubstituteGroupVO.MemberVO> selectMembersByGroupOids(@Param("groupOids") String groupOids);

    @Override
    @Insert("INSERT INTO " + MEMBER_TABLE + " (oid, group_oid, member_side, bom_link_oid, part_oid, "
            + "quantity, unit, sort_order, creator, created_at, updater, updated_at) VALUES ("
            + "#{oid}, #{groupOid}, #{memberSide}, #{bomLinkOid}, #{partOid}, #{quantity}, "
            + "#{unit}, #{sortOrder}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insertMember(BomSubstituteGroupMember member);

    @Override
    @Delete("DELETE FROM " + MEMBER_TABLE + " WHERE group_oid = #{groupOid}")
    int deleteMembersByGroupOid(@Param("groupOid") String groupOid);

    /**
     * 行内标记用的批量计数：只数原料侧（SOURCE）成员 —— 界面上那个标记回答的是
     * "这一行被卷进了几个成组替代"，替代侧的成员行并不挂在这一行上。
     *
     * <p>同一个组理论上可能把同一行登记两次（表上没有唯一约束），用 {@code COUNT(DISTINCT group_oid)}
     * 保证数字含义是"几个组"而不是"几条成员行"。
     */
    @Override
    @Select("SELECT m.bom_link_oid AS \"bomLinkOid\", COUNT(DISTINCT m.group_oid) AS \"groupCount\" "
            + "FROM " + MEMBER_TABLE + " m "
            + "WHERE m.bom_link_oid::text = ANY(string_to_array(#{bomLinkOids}, ',')) "
            + "AND m.member_side = 'SOURCE' "
            + "GROUP BY m.bom_link_oid")
    List<Map<String, Object>> countGroupsByBomLinkOids(@Param("bomLinkOids") String bomLinkOids);
}
