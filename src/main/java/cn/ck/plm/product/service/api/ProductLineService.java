/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.api;

import cn.ck.plm.product.dto.TeamMemberVO;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.Team;

import java.util.List;
import java.util.Map;

/**
 * 产品线管理服务接口，提供产品线与团队的 CRUD 能力。
 */
public interface ProductLineService {

    // ===== 产品线 =====
    ProductLine create(ProductLine productLine);

    ProductLine update(ProductLine productLine);

    boolean delete(String oid);

    ProductLine findByOid(String oid);

    List<ProductLine> findAll();

    List<ProductLine> search(String keyword);

    /** 查询产品线树（嵌套 children 结构，含子系列 + 产品型号） */
    List<ProductLine> findTree();

    /** 查询纯产品系列树（嵌套 children 结构，仅含子系列，不含产品型号，用于 product-line-select） */
    List<ProductLine> findLinesOnlyTree();

    /** 查询根节点列表 */
    List<ProductLine> findRoots();

    /** 查询指定父节点的直接子节点 */
    List<ProductLine> findChildren(String parentOid);

    /** 批量获取产品线统计（子系列数量 + 产品型号数量） */
    Map<String, Map<String, Integer>> getStats();

    // ===== 团队管理 =====

    /** 获取产品线关联的团队 */
    Team getTeamByProductLineOid(String productLineOid);

    /** 获取团队成员列表（含用户信息及角色名称） */
    List<TeamMemberVO> getTeamMembers(String productLineOid);

    /** 添加团队成员 */
    void addTeamMember(String productLineOid, String userId, String roleName);

    /** 移除团队成员 */
    void removeTeamMember(String productLineOid, String userId);
}
