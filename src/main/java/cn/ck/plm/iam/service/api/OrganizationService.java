/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.api;

import cn.ck.plm.iam.entity.Organization;

import java.util.List;

/**
 * 组织架构服务接口，提供组织的 CRUD 及树形查询能力。
 *
 * <p>组织支持树形层级结构，通过 {@code parentOid} 建立父子关系。
 */
public interface OrganizationService {

    Organization create(Organization org);

    Organization update(Organization org);

    boolean delete(String oid);

    Organization findByOid(String oid);

    Organization findByCode(String code);

    List<Organization> findAll();

    List<Organization> findChildren(String parentOid);

    List<Organization> findRoots();

    List<Organization> findTree();

    List<Organization> search(String keyword);

    boolean existsByCode(String code);
}
