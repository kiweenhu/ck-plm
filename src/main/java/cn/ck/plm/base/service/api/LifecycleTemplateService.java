/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.service.MasterService;

import java.util.List;

/**
 * 生命周期模板服务接口，扩展 MasterService 的版本控制能力。
 */
public interface LifecycleTemplateService extends MasterService {

    /** 创建模板（含初始子版本、状态关联和流转规则） */
    LifecycleTemplateMaster create(LifecycleTemplateMaster template);

    /** 更新模板（全量替换状态和流转规则） */
    LifecycleTemplateMaster update(LifecycleTemplateMaster template);

    /** 删除模板（级联删除子版本、状态关联和流转规则） */
    boolean delete(String code);

    /** 按编码查询（含 states、transitions、rejections、latestIteration） */
    LifecycleTemplateMaster findByCode(String code);

    /** 查询所有模板（含 states、transitions、rejections、latestIteration） */
    List<LifecycleTemplateMaster> findAll();

    /** 模糊搜索 */
    List<LifecycleTemplateMaster> search(String keyword);

    /** 判断编码是否已存在 */
    boolean exists(String code);

    /**
     * 根据 typeDefinitionCode 查找绑定的生命周期模板，初始化迭代记录的生命周期状态。
     * 设置迭代的 lifecycleTemplateIterationOid 和初始 status。
     *
     * @param iter     迭代实体
     * @param typeCode 类型定义编码
     */
    void initLifecycle(IterationEntity iter, String typeCode);
}
