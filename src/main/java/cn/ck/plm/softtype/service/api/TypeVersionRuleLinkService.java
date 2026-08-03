/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.TypeVersionRuleLink;

import java.util.List;

/**
 * 类型-版本规则关联服务接口。
 *
 * <p>管理 TypeDefinition 与 VersionRule 的绑定关系，
 * 为未来业务对象创建时自动生成编码提供规则查找能力。
 */
public interface TypeVersionRuleLinkService {

    /**
     * 为类型绑定版本规则（若已绑定则更新）
     *
     * @param typeOid         类型 OID
     * @param versionRuleCode 版本规则编码
     * @return 关联记录
     */
    TypeVersionRuleLink bindRule(String typeOid, String versionRuleCode);

    /**
     * 解除类型的版本规则绑定
     *
     * @param typeOid 类型 OID
     */
    void unbindRule(String typeOid);

    /**
     * 根据类型 OID 查询已绑定的版本规则
     *
     * @param typeOid 类型 OID
     * @return 关联记录，未绑定时返回 null
     */
    TypeVersionRuleLink getByTypeOid(String typeOid);

    /**
     * 根据版本规则编码查询所有绑定了该规则的类型
     *
     * @param versionRuleCode 版本规则编码
     * @return 关联记录列表
     */
    List<TypeVersionRuleLink> listByVersionRuleCode(String versionRuleCode);

    /**
     * 查询所有关联
     */
    List<TypeVersionRuleLink> listAll();
}
