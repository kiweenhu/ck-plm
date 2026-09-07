/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.PartAlternateLink;

import java.util.List;

/**
 * PartAlternateLink（部件双向替代关系）数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>对应表 {@code ck_part_alternate_link}。替代关系为对称双向存储：
 * roleA / roleB 为有序对（roleA &lt; roleB），数据库层通过唯一约束防重复。
 */
public interface PartAlternateLinkMapper {

    int insert(PartAlternateLink link);

    int update(PartAlternateLink link);

    int deleteByOid(String oid);

    PartAlternateLink selectByOid(String oid);

    /** 查询某部件作为 roleA（被替代方，roleB 为其替代件）的全部替代关系 */
    List<PartAlternateLink> selectByRoleAPartOid(String roleAPartOid);

    /** 查询某部件作为 roleB（替代方，roleA 为被其替代的部件）的全部替代关系 */
    List<PartAlternateLink> selectByRoleBPartOid(String roleBPartOid);

    /** 查询与某部件相关的全部替代关系（无论角色端） */
    List<PartAlternateLink> selectByPartOid(String partOid);

    List<PartAlternateLink> selectAll();
}
