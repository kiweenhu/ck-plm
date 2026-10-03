/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper;

import cn.ck.plm.bom.dto.BomSubstituteVO;
import cn.ck.plm.bom.entity.BomSubstituteLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 局部替代（BOM 行替代）持久化接口。
 *
 * <p>表 {@code ck_bom_substitute_link} 自带 {@code (bom_link_oid, substitute_part_oid)} 唯一索引，
 * 但不靠它兜底判重：命中重复时应"更新参数"（「设置替代」的语义）而不是把数据库唯一约束异常
 * 抛给用户。判重靠 {@link #selectVoByBomLinkOid(String)} 的一行清单（一个 BOM 行的替代件本就不多），
 * 因此不需要按 oid / 按 pair 再查实体（实体查询还要额外维护列映射）。
 */
public interface BomSubstituteLinkMapper {

    int insert(BomSubstituteLink link);

    /** 按 oid 更新可变字段（类型/数量/单位/优先级/启用/说明），不含挂载点与两端零件 */
    int update(BomSubstituteLink link);

    /** 某 BOM 行的替代件清单（带两端零件的展示信息，按优先级/创建时间排序） */
    List<BomSubstituteVO> selectVoByBomLinkOid(@Param("bomLinkOid") String bomLinkOid);

    int deleteByOid(@Param("oid") String oid);

    /** 清空某 BOM 行的全部替代（「取消替代」） */
    int deleteByBomLinkOid(@Param("bomLinkOid") String bomLinkOid);

    /**
     * 批量统计若干 BOM 行的替代件数量（BOM 树一次查完，避免逐行查询的 N+1）。
     *
     * <p>入参是<b>逗号拼接的 oid 串</b>而不是 {@code List}：注解 SQL 里写 {@code <foreach>}
     * 会让"SQL 整串交给租户拦截器按正则改写"这个前提变得不确定，所以改用 PostgreSQL 的
     * {@code = ANY(string_to_array(...))} —— SQL 仍是一段纯文本。
     *
     * @return 每个"有替代件"的 BOM 行一个 Map：{@code bomLinkOid}、
     *         {@code substituteCount}（含停用）、{@code substituteEnabledCount}（启用中）
     */
    List<Map<String, Object>> countByBomLinkOids(@Param("bomLinkOids") String bomLinkOids);
}
