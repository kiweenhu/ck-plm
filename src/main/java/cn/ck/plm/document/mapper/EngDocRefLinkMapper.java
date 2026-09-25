/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.EngDocRefLink;

import java.util.List;

/**
 * {@link EngDocRefLink}（工程数据引用链接）数据访问契约，数据库无关。
 *
 * <p>对应表 {@code ck_eng_doc_ref_link}（对齐 Windchill {@code EPMReferenceLink}）。
 * 角色：roleA = 发起引用的迭代（{@code referenced_by_iteration_oid}），
 * roleB = 被引用主对象（{@code references_master_oid}）。
 */
public interface EngDocRefLinkMapper {

    int insert(EngDocRefLink link);

    int update(EngDocRefLink link);

    int deleteByOid(String oid);

    /** 删除某迭代发出的全部引用（用于整组重建） */
    int deleteByReferencedByIterationOid(String referencedByIterationOid);

    EngDocRefLink selectByOid(String oid);

    /** 正向导航：某迭代引用了哪些对象 */
    List<EngDocRefLink> selectByReferencedByIterationOid(String referencedByIterationOid);

    /** 反向导航：某主对象被哪些迭代引用（影响分析入口） */
    List<EngDocRefLink> selectByReferencesMasterOid(String referencesMasterOid);

    /** 按引用类型检索（对齐 Windchill 的 compositeIndex6 查询模式） */
    List<EngDocRefLink> selectByReferenceType(String referenceType);
}
