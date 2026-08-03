/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Team;

import java.util.List;

/**
 * 团队数据访问接口。
 */
public interface TeamMapper {

    int insert(Team team);

    int update(Team team);

    int deleteByOid(String oid);

    Team selectByOid(String oid);

    Team selectByCode(String code);

    List<Team> selectAll();

    int existsByCode(String code);
}
