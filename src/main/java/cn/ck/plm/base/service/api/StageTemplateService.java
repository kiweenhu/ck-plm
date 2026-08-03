/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.StageTemplate;

import java.util.List;

/**
 * 研发阶段模板服务接口。
 */
public interface StageTemplateService {

    StageTemplate create(StageTemplate template);

    StageTemplate update(StageTemplate template);

    boolean delete(String oid);

    StageTemplate findByOid(String oid);

    StageTemplate findByCode(String code);

    List<StageTemplate> findAll();

    /** 初始化平台级默认阶段模板（幂等） */
    int initPlatformDefaults();

    /** 租户克隆平台级模板到本租户（幂等：已存在同code的租户模板则跳过） */
    int cloneFromPlatform();

    /** 查询平台级模板（用于 fallback 初始化） */
    List<StageTemplate> findPlatformTemplates();
}
