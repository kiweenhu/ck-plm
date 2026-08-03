/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.api;

import cn.ck.plm.product.dto.TeamMemberVO;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.entity.Team;

import java.util.List;

/**
 * 产品型号管理服务接口，提供产品型号的 CRUD 及团队管理能力。
 */
public interface ProductModelService {

    // ===== 产品型号 =====
    ProductModel create(ProductModel model);

    ProductModel update(ProductModel model);

    boolean delete(String oid);

    ProductModel findByOid(String oid);

    List<ProductModel> findAll();

    /** 按所属产品系列查询 */
    List<ProductModel> findByProductLineOid(String productLineOid);

    List<ProductModel> search(String keyword);

    // ===== 团队管理 =====

    /** 获取产品型号关联的团队 */
    Team getTeamByProductModelOid(String modelOid);

    /** 获取团队成员列表（含用户信息及角色名称） */
    List<TeamMemberVO> getTeamMembers(String modelOid);

    /** 添加团队成员 */
    void addTeamMember(String modelOid, String userId, String roleName);

    /** 移除团队成员 */
    void removeTeamMember(String modelOid, String userId);
}
