/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.Organization;

import java.util.List;

/**
 * 组织架构数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>由 {@code mapper.impl.PostgreSqlOrganizationMapper} 对接 PostgreSQL。
 */
public interface OrganizationMapper {

    int insert(Organization org);

    int update(Organization org);

    int deleteByOid(String oid);

    Organization selectByOid(String oid);

    Organization selectByCode(String code);

    List<Organization> selectAll();

    List<Organization> selectByParentOid(String parentOid);

    List<Organization> selectRoots();

    List<Organization> search(String keyword);

    int existsByCode(String code);
}
