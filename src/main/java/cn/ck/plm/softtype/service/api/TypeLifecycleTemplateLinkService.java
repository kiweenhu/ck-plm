/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;

import java.util.List;

/**
 * 类型-生命周期模板关联服务接口。
 *
 * <p>管理 TypeDefinition 与 LifecycleTemplate 的绑定关系，
 * 为业务对象生命周期状态流转提供模板查找能力。
 */
public interface TypeLifecycleTemplateLinkService {

    /**
     * 为类型绑定生命周期模板（若已绑定则更新）
     *
     * @param typeOid               类型 OID
     * @param lifecycleTemplateCode 生命周期模板编码
     * @return 关联记录
     */
    TypeLifecycleTemplateLink bindTemplate(String typeOid, String lifecycleTemplateCode);

    /**
     * 解除类型的生命周期模板绑定
     *
     * @param typeOid 类型 OID
     */
    void unbindTemplate(String typeOid);

    /**
     * 根据类型 OID 查询已绑定的生命周期模板
     *
     * @param typeOid 类型 OID
     * @return 关联记录，未绑定时返回 null
     */
    TypeLifecycleTemplateLink getByTypeOid(String typeOid);

    /**
     * 根据生命周期模板编码查询所有绑定了该模板的类型
     *
     * @param lifecycleTemplateCode 生命周期模板编码
     * @return 关联记录列表
     */
    List<TypeLifecycleTemplateLink> listByTemplateCode(String lifecycleTemplateCode);

    /**
     * 查询所有关联
     */
    List<TypeLifecycleTemplateLink> listAll();
}
