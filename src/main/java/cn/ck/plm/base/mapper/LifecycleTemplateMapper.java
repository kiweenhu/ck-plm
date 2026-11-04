/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.entity.LifecycleTemplateTransitionRef;
import org.apache.ibatis.annotations.Param;

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

    /** 按 oid 查询（由「生命周期模板子版本 → 主档」反查时用） */
    LifecycleTemplateMaster selectByOid(String oid);
    List<LifecycleTemplateMaster> selectAll();
    List<LifecycleTemplateMaster> search(String keyword);
    int existsByCode(String code);

    // --- 模板状态关联 ---
    int insertStateRef(LifecycleTemplateStatusRef ref);
    int deleteStateRefsByIterationOid(String iterationOid);
    List<LifecycleTemplateStatusRef> selectStateRefsByIterationOid(String iterationOid);

    /**
     * 就地更新某状态的可修订标记（不产生新的模板子版本）。
     *
     * <p>「可修订」是权限开关而不是结构变更：租户管理员改它不该像增删状态那样
     * 把模板版本往上推。调用方会遍历该模板的<b>全部子版本</b>逐一更新，
     * 让已经固化了旧子版本的在途对象也立即按新规则判定。
     */
    int updateStateRevisable(@Param("iterationOid") String iterationOid,
                             @Param("statusCode") String statusCode,
                             @Param("revisable") boolean revisable);

    // --- 模板流转规则 ---
    int insertTransitionRef(LifecycleTemplateTransitionRef ref);
    int deleteTransitionRefsByIterationOid(String iterationOid);
    List<LifecycleTemplateTransitionRef> selectTransitionRefsByIterationOid(String iterationOid);
}
