/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.api;

import cn.ck.plm.part.entity.PartAlternateLink;

import java.util.List;

/**
 * PartAlternateLink（部件双向替代关系）服务契约。
 *
 * <p>提供全局双向替代关系的 CRUD 与按角色端查询。替代关系挂在部件主数据
 * （Master）级别，不限定于某个 BOM，任意 BOM 中遇到任一部件均可引用。
 */
public interface PartAlternateLinkService {

    /** 创建双向替代关系（自动规范化 roleA &lt; roleB 有序对） */
    PartAlternateLink create(PartAlternateLink link);

    /** 更新双向替代关系 */
    PartAlternateLink update(PartAlternateLink link);

    /** 删除双向替代关系 */
    void delete(String oid);

    /** 按 oid 查询 */
    PartAlternateLink findByOid(String oid);

    /** 查询某部件作为 roleA（被替代方，roleB 为其替代件）的全部替代关系 */
    List<PartAlternateLink> findByRoleAPart(String roleAPartOid);

    /** 查询某部件作为 roleB（替代方，roleA 为被其替代的部件）的全部替代关系 */
    List<PartAlternateLink> findByRoleBPart(String roleBPartOid);

    /** 查询与某部件相关的全部替代关系（无论角色端） */
    List<PartAlternateLink> findByPart(String partOid);

    List<PartAlternateLink> listAll();
}
