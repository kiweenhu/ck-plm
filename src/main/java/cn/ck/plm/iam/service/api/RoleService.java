/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.api;

import cn.ck.plm.iam.entity.Role;

import java.util.List;

/**
 * 角色管理服务接口，提供角色的 CRUD 能力。
 */
public interface RoleService {

    Role create(Role role);

    Role update(Role role);

    boolean delete(String oid);

    Role findByOid(String oid);

    Role findByCode(String code);

    List<Role> findAll();

    List<Role> findByUser(String userOid);

    List<Role> search(String keyword);

    boolean existsByCode(String code);
}
