/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;

import java.util.List;

/**
 * 资源安装器（SPI）—— 一类数据模板的"幂等落地"逻辑。
 *
 * <h3>为什么做成 SPI 而不是一个巨型 if-else</h3>
 * 行业包有十余类资源（软类型 / 属性 / 分类 / 编码规则 / 版本规则 / 生命周期 / 阶段 / 资源库 /
 * 角色 / 问题类型 / 交付物 / 门 / 评审 / 规则 / 指标 / 流程 / 示例）。每类的"业务键是什么、
 * 落到哪张表、怎么写才不重复"各不相同，天然是一族策略；分派键就是 {@code pack.json} 里
 * {@code resources[].type}。新增一类资源 = 新增一个实现类，引擎零改动。
 *
 * <h3>幂等契约（每个实现必须遵守）</h3>
 * <ol>
 *   <li><b>按业务键判断</b>：已存在 → 返回 {@link InstallResult.Action#SKIP}（<b>不覆盖</b>，
 *       保留用户在界面上改过的值）；不存在 → 创建并返回 {@code INSERT}；</li>
 *   <li><b>可空手返回</b>：{@link InstallContext#isDryRun()} 为真时<b>只算不写</b>（用于引导页"预演"），
 *       此时 action 表达"如果真装会做什么"；</li>
 *   <li><b>单条失败不抛出</b>：返回 {@code FAILED} + message，引擎照常继续装其余条目
 *       —— 一个包里的 46 项交付物不该因为第 7 项写坏而整体失败。</li>
 * </ol>
 */
public interface ResourceInstaller {

    /** 分派键 —— 与 {@code pack.json} 的 {@code resources[].type} 大小写不敏感匹配 */
    String resourceType();

    /**
     * 安装一条资源声明（一个模板文件）。
     *
     * <p><b>返回的是逐条明细</b>，不是一个汇总：一个 {@code numberRules.json} 里装着 6 条规则，
     * 逐条明细才能支撑三件事 —— 引导页按条"播放"、重复安装逐条判 SKIP、失败时只回滚/重试那一条。
     *
     * @param context 安装上下文（包、租户、参数、是否预演、落库工具）
     * @param resource 清单里的资源声明（含文件名与期望条数）
     * @return 每一条模板数据的落地结果（写入 {@code ck_industry_pack_resource}）
     */
    List<InstallResult> install(InstallContext context, PackManifest.Resource resource);
}
