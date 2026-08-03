/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.TeamMember;

import java.util.List;

/**
 * 团队成员数据访问接口。
 */
public interface TeamMemberMapper {

    int insert(TeamMember member);

    int deleteByTeamAndUser(String teamOid, String userId);

    int deleteByTeamOid(String teamOid);

    List<TeamMember> selectByTeamOid(String teamOid);

    boolean existsByTeamAndUser(String teamOid, String userId);
}
