/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.EngDocMemberLink;

import java.util.List;

/**
 * {@link EngDocMemberLink}（工程数据装配成员链接）数据访问契约，数据库无关。
 *
 * <p>对应表 {@code ck_eng_doc_member_link}（对齐 Windchill {@code EPMMemberLink}）。
 * 角色：roleA = 父装配迭代（{@code used_by_iteration_oid}），roleB = 子件主对象（{@code uses_master_oid}）。
 */
public interface EngDocMemberLinkMapper {

    int insert(EngDocMemberLink link);

    int update(EngDocMemberLink link);

    int deleteByOid(String oid);

    /** 删除某装配迭代下的全部成员（用于整树重建） */
    int deleteByUsedByIterationOid(String usedByIterationOid);

    EngDocMemberLink selectByOid(String oid);

    /** 向下导航：某装配迭代的<b>直接</b>成员列表 */
    List<EngDocMemberLink> selectByUsedByIterationOid(String usedByIterationOid);

    /** 向上导航：某子件主对象被哪些装配迭代所使用 */
    List<EngDocMemberLink> selectByUsesMasterOid(String usesMasterOid);
}
