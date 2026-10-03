/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper;

import cn.ck.plm.bom.dto.BomSubstituteGroupVO;
import cn.ck.plm.bom.entity.BomSubstituteGroup;
import cn.ck.plm.bom.entity.BomSubstituteGroupMember;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 成组替代（组头 {@code ck_bom_substitute_group} + 成员 {@code ck_bom_substitute_group_member}）
 * 持久化接口。
 *
 * <p>几条与局部替代一致的约定（原因见 {@code PostgreSqlBomSubstituteLinkMapper} 的类注释）：
 * 查询只走 VO / Map 不做实体映射（省掉一套列映射）、不写子查询、别名用带双引号的驼峰、
 * {@code tenant_oid} 不在 SQL 里手写（拦截器统一注入）。
 *
 * <p>成员<b>整体替换</b>：不提供"改单条成员"的方法 —— 编辑一个组在界面上就是重新挑一遍
 * 两侧成员，增量的 diff 只会把简单事做复杂。
 */
public interface BomSubstituteGroupMapper {

    int insertGroup(BomSubstituteGroup group);

    /** 只更新可变字段（名称/说明/状态/整组替换/启用），不动挂载点与成员 */
    int updateGroup(BomSubstituteGroup group);

    /** 删除组头（成员由外键 ON DELETE CASCADE 一并清理） */
    int deleteGroupByOid(@Param("oid") String oid);

    /** 单个组头（不含成员） */
    BomSubstituteGroupVO selectVoByOid(@Param("oid") String oid);

    /**
     * 某父件迭代下的全部组头（不含成员）。
     *
     * <p>成员用 {@link #selectMembersByGroupOids(String)} 批量取一次，两条 SQL 拼出完整结构 ——
     * 逐个组查成员就是 N+1。
     */
    List<BomSubstituteGroupVO> selectVoByParentIterationOid(@Param("parentIterationOid") String parentIterationOid);

    /**
     * 批量取成员（带两侧展示信息）。
     *
     * @param groupOids 逗号拼接的组 oid 串（为什么不用 List + foreach，见局部替代的同类方法注释）
     */
    List<BomSubstituteGroupVO.MemberVO> selectMembersByGroupOids(@Param("groupOids") String groupOids);

    int insertMember(BomSubstituteGroupMember member);

    /** 清空某组的全部成员（整体替换时先删后插） */
    int deleteMembersByGroupOid(@Param("groupOid") String groupOid);

    /**
     * 批量统计：每个 BOM 行被多少个成组替代组当作<b>原料侧</b>成员引用。
     *
     * <p>BOM 树的行内标记要用它（"这行还被卷进了几个成组替代"），所以必须一次查完 ——
     * 逐行查就是 N+1。
     *
     * @param bomLinkOids 逗号拼接的 BOM 行 oid 串
     * @return 每个命中的行一个 Map：{@code bomLinkOid} / {@code groupCount}
     */
    List<Map<String, Object>> countGroupsByBomLinkOids(@Param("bomLinkOids") String bomLinkOids);
}
