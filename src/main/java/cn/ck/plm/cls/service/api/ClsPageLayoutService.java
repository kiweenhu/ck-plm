/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cls.service.api;

import cn.ck.plm.cls.entity.ClsPageLayout;

import java.util.List;
import java.util.Map;

/**
 * 分类 IBA 页面布局服务接口。
 */
public interface ClsPageLayoutService {

    /** 保存或更新布局（clsOid + operationCode 唯一） */
    ClsPageLayout saveOrUpdate(ClsPageLayout layout);

    /** 根据分类 + 操作码查询布局 */
    ClsPageLayout findByClsAndOperation(String clsOid, String operationCode);

    /** 查询分类的所有操作布局 */
    List<ClsPageLayout> findAllByClsOid(String clsOid);

    /** 删除某分类的某操作布局 */
    void deleteByClsAndOperation(String clsOid, String operationCode);

    /** 获取分类的操作摘要列表 */
    List<Map<String, String>> getOperationSummary(String clsOid);

    /** 删除分类的所有布局 */
    void deleteAllByClsOid(String clsOid);
}
