/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.TypeLifecycleStateProcessLink;

import java.util.List;
import java.util.Map;

/**
 * 类型-生命周期状态-流程模板 关联服务（1:1）。
 *
 * <p>语义：**某个类型**在其**生命周期模板的某一版（子版本）**下，某个状态「用哪个流程模板」。
 * 类型与版本两个维度都不能省：
 * <ul>
 *   <li>省掉类型 → 同一模板被多个类型复用时，无法各自指定；</li>
 *   <li>省掉版本 → 与业务对象迭代固化的 {@code lifecycleTemplateIterationOid} 不同层，
 *       运行期无法按实例自带的版本精确解析（改配置会追溯性改写在途实例的行为）。</li>
 * </ul>
 *
 * <p>本服务归位在<b>类型模块</b>（与 {@link TypeLifecycleTemplateLinkService} 同族）：
 * 只有在这里，才能直接读「类型 → 生命周期模板」关联，自己解析出该类型当前用的是哪个模板、
 * 最新子版本是哪个、归属哪个租户 —— 调用方只需给出类型 oid 与状态。
 *
 * <p>写入位置与读取位置一致地取<b>该类型当前生效的那一版</b>
 * （见实现里的"生效子版本"解析）：正常情况下就是最新子版本；
 * 若因绕过本系统的编辑导致最新版没有配置，则沿用配置所在的那一版，避免同一份配置被拆到两版。
 *
 * <p>本期只做<b>配置与展示</b>：发起流程仍由人工手动发起，运行期自动触发未接入。
 */
public interface TypeLifecycleStateProcessService {

    /**
     * 某类型（按其当前所绑生命周期模板的生效子版本）的「状态 → 流程模板」映射。
     *
     * @return key = 状态 code，value = 流程模板 oid；未配置的状态不在其中。
     *         该类型还没绑生命周期模板、或模板没有任何子版本时返回空映射
     */
    Map<String, String> mapByType(String typeOid);

    /**
     * 配置 / 改配 / 解配某类型的某状态（返回更新后的整张映射，供前端一次刷新）。
     *
     * <p>会校验：类型已绑生命周期模板、状态属于该模板（防止配到已删除的状态）。
     *
     * @param processTemplateOid 目标流程模板 oid；<b>传空即解配</b>
     */
    Map<String, String> bind(String typeOid, String statusCode, String processTemplateOid);

    /**
     * 清掉某类型的全部配置（类型改绑到别的生命周期模板 / 解绑模板时调用）。
     *
     * <p>不清就会留下"界面上看不到、却仍挡着流程模板删除"的幽灵引用。
     */
    void clearByType(String typeOid);

    /**
     * 清掉挂在指定生命周期模板子版本上的全部配置（生命周期模板被删除时调用）。
     *
     * <p>子版本会被外键级联删掉，而本表只以子版本 oid 作<b>软引用</b>（无外键），
     * 不主动清就会留下永远解析不到的垃圾行。
     */
    void clearByIterations(List<String> lifecycleTemplateIterationOids);

    /**
     * 运行期解析：该类型在该状态下<b>实际生效</b>的配置（供业务对象的「发起流程」入口使用）。
     *
     * <p>两步解析：
     * <ol>
     *   <li>传了 {@code iterationOid}（业务对象迭代固化的子版本）且该版本有配置 → 用它
     *       —— 那是该对象"出生"时那一版的配置，不该被后来的改配置追溯性改写；</li>
     *   <li>否则回落到该类型当前（最新）子版本的配置 —— 状态是后来才配的，也要能发起。</li>
     * </ol>
     *
     * @return 命中的配置行（含 {@code lifecycleTemplateIterationOid} 与 {@code processTemplateOid}）；
     *         该状态下没有任何配置时返回 {@code null}
     */
    TypeLifecycleStateProcessLink resolveLink(String typeOid, String statusCode, String iterationOid);

    /**
     * 该流程模板被多少处「类型 · 状态」配置引用（删除流程模板前检查；0 表示可以删）。
     *
     * <p>只数<b>生效版本</b>上的配置，并按「类型 + 状态」去重：
     * 继承会让同一份配置在多个子版本各留一行（历史留痕，供在途实例按旧子版本解析）；
     * 若把历史行也算进来，用户会在"界面上找不到引用处"的情况下删不掉流程模板，
     * 而按行计数还会把"1 处"报成"N 处"。
     */
    int countByProcessTemplate(String processTemplateOid);

    /**
     * 生命周期模板产生新子版本时，把「状态 → 流程」配置继承到新子版本。
     *
     * <p>为什么必须继承：模板的每次编辑都会整体重建状态行并生成新子版本，而配置挂的是子版本 ——
     * 不继承的话，用户一编辑模板，配置就留在旧版本上（界面上看不到，实例也解析不到）。
     *
     * <p>只继承新版本里<b>仍然存在</b>的状态；旧版本的行<b>保留不动</b>
     * —— 在途实例固化的是旧子版本 oid，仍要能解析出当时那一版配置。
     *
     * @param oldIterationOid 旧子版本 oid（配置来源）
     * @param newIterationOid 新子版本 oid（配置去向）
     */
    void inherit(String oldIterationOid, String newIterationOid);
}
