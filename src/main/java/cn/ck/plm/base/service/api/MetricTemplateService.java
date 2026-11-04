/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.MetricTemplate;

import java.util.List;

/**
 * 指标（驾驶舱）模板服务 —— 与前面六类模板服务同型：
 * 平台一份供克隆，租户一份可裁剪（改 / 停用 / 删除）。
 *
 * <p>指标与其他模板的差别在于它是<b>计算口径 + 数据就绪度</b>：
 * 裁剪时改的是"怎么算、算不算得出"，而 {@code level = C} 的指标按文档硬规则
 * <b>只显示"暂未评估"，禁止编造数值</b>。
 */
public interface MetricTemplateService {

    /** 新建（幂等键 {@code code + tenant}；平台已有同码时禁止新建，请先克隆） */
    MetricTemplate create(MetricTemplate template);

    /** 更新（patch 语义：字段缺席=不改、空串=清空；code 与归属租户不可改） */
    MetricTemplate update(MetricTemplate template);

    /** 删除（不推荐：包重装会把它加回来；日常请用 {@link #setDisabled}） */
    boolean delete(String oid);

    /** 停用/启用（企业裁剪的正道：保留行与幂等键，重装不会复活） */
    boolean setDisabled(String oid, boolean disabled);

    /** 本租户全部指标（含已停用，界面可切换显示） */
    List<MetricTemplate> findAll();

    /** 本租户指标按行业/方法论过滤（空 = 不过滤；另含 industry 为空的"通用"条目 —— 基础包共用指标即通用） */
    List<MetricTemplate> findAll(String industry);

    /** 按 code 取一条：本租户优先，缺则回落平台那份 */
    MetricTemplate findByCode(String code);

    /** 平台已提供的行业/方法论列表（克隆弹窗的选项来源） */
    List<String> listPlatformIndustries();

    /** 平台级指标（按 industry 过滤，空返回全部） */
    List<MetricTemplate> findPlatformTemplates(String industry);

    /** 克隆全部平台模板到本租户 */
    int cloneFromPlatform();

    /** 克隆指定行业/方法论的平台模板到本租户（已存在的 code 跳过，不覆盖企业裁剪） */
    int cloneFromPlatform(String industry);
}
