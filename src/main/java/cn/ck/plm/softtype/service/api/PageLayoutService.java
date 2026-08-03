/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.PageLayout;
import java.util.List;
import java.util.Map;

/**
 * 页面布局服务接口。
 */
public interface PageLayoutService {

    /** 保存或更新页面布局（entityOid + operationCode 唯一） */
    PageLayout saveOrUpdate(PageLayout layout);

    /** 根据实体 + 操作码查询布局 */
    PageLayout findByEntityAndOperation(String entityOid, String operationCode);

    /** 查询实体的所有操作布局列表 */
    List<PageLayout> findAllByEntity(String entityOid, String entityCode);

    /** 删除某实体的某操作布局 */
    void deleteByEntityAndOperation(String entityOid, String operationCode);

    /** 获取实体的操作摘要列表（用于下拉选择器） */
    List<Map<String, String>> getOperationSummary(String entityOid, String entityCode);

    /** 根据实体编码 + 操作码查询布局 */
    PageLayout findByEntityCodeAndOperation(String entityCode, String operationCode);

    /** 克隆平台级页面布局到当前租户 */
    PageLayout cloneFromPlatform(String entityOid, String entityCode, String operationCode);
}
