/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.StageTemplate;

import java.util.List;

/**
 * 研发阶段模板数据访问接口。
 */
public interface StageTemplateMapper {

    int insert(StageTemplate template);

    int update(StageTemplate template);

    int deleteByOid(String oid);

    StageTemplate selectByOid(String oid);

    StageTemplate selectByCode(String code, String tenantOid, String platformOid);

    /** 查询本租户的全部模板，按 sortOrder 排序 */
    List<StageTemplate> selectAll(String tenantOid, String platformOid);

    /** 查询指定租户的全部模板（用于克隆时获取平台模板） */
    List<StageTemplate> selectByTenant(String tenantOid);

    int existsByCode(String code, String tenantOid, String platformOid);
}
