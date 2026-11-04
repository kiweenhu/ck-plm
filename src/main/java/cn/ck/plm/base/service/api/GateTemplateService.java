/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.GateTemplate;

import java.util.List;

/**
 * 阶段门（评审门）模板服务 —— 与 {@link StageTemplateService} / {@link DeliverableTemplateService} 同型：
 * 平台一份供克隆，租户一份可裁剪（改 / 停用 / 删除），后续按它实例化到产品系列或型号。
 */
public interface GateTemplateService {

    /** 新建（幂等键 {@code code + tenant}；平台已有同码时禁止新建，请先克隆） */
    GateTemplate create(GateTemplate template);

    /** 更新（patch 语义：字段缺席=不改、空串=清空；code 与归属租户不可改） */
    GateTemplate update(GateTemplate template);

    /** 删除（不推荐：包重装会把它加回来；日常请用 {@link #setDisabled}） */
    boolean delete(String oid);

    /** 停用/启用（企业裁剪的正道：保留行与幂等键，重装不会复活） */
    boolean setDisabled(String oid, boolean disabled);

    /** 本租户的全部门模板（含已停用，界面可切换显示） */
    List<GateTemplate> findAll();

    /** 本租户门模板按行业/方法论过滤（空 = 不过滤；另含 industry 为空的"通用"条目） */
    List<GateTemplate> findAll(String industry);

    /** 按 code 取一条：本租户优先，缺则回落平台那份 */
    GateTemplate findByCode(String code);

    /** 平台已提供的行业/方法论列表（克隆弹窗的选项来源） */
    List<String> listPlatformIndustries();

    /** 平台级门模板（按 industry 过滤，空返回全部） */
    List<GateTemplate> findPlatformTemplates(String industry);

    /** 克隆全部平台模板到本租户 */
    int cloneFromPlatform();

    /** 克隆指定行业/方法论的平台模板到本租户（已存在的 code 跳过，不覆盖企业裁剪） */
    int cloneFromPlatform(String industry);
}
