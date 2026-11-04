/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

    /**
     * 克隆「**根类型布局**」到当前类型（当前租户内）。
     *
     * <p>根类型 = 当前类型的 {@code root_type_code}（能力宿主，如 电子元器件 → PART）。
     * 2026-10-11 由"从平台租户母版克隆"改为此口径 —— 与全库"按租户隔离、不拿平台兜底"的原则一致：
     * 平台那份布局对租户不可见、也不该作为克隆来源；布局应当从**本租户自己的根类型**继承。
     *
     * <p>约束：当前类型已有该操作布局时拒绝（要先删除）；根类型自身、根类型缺该操作布局时如实报错。
     */
    PageLayout cloneFromRootType(String entityOid, String entityCode, String operationCode);
}
