/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

    /**
     * 某用户（username）加入的全部团队 oid。
     *
     * <p>2026-10-12 新增：产品系列 / 型号的列表按团队可见性过滤用 ——
     * "不在系列/型号的团队中，就不该看到对应记录"（用户要求）。
     */
    List<String> selectTeamOidsByUserId(String userId);
}
