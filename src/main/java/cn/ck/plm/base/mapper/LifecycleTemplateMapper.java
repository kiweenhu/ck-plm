/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.entity.LifecycleTemplateTransitionRef;

import java.util.List;

/**
 * 生命周期模板数据访问接口。
 */
public interface LifecycleTemplateMapper {

    // --- 模板主表 ---
    int insert(LifecycleTemplateMaster template);
    int update(LifecycleTemplateMaster template);
    int deleteByCode(String code);
    LifecycleTemplateMaster selectByCode(String code);
    List<LifecycleTemplateMaster> selectAll();
    List<LifecycleTemplateMaster> search(String keyword);
    int existsByCode(String code);

    // --- 模板状态关联 ---
    int insertStateRef(LifecycleTemplateStatusRef ref);
    int deleteStateRefsByIterationOid(String iterationOid);
    List<LifecycleTemplateStatusRef> selectStateRefsByIterationOid(String iterationOid);

    // --- 模板流转规则 ---
    int insertTransitionRef(LifecycleTemplateTransitionRef ref);
    int deleteTransitionRefsByIterationOid(String iterationOid);
    List<LifecycleTemplateTransitionRef> selectTransitionRefsByIterationOid(String iterationOid);
}
