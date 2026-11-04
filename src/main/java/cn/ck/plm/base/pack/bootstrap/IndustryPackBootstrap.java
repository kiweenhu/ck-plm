/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.bootstrap;

import cn.ck.plm.base.pack.engine.IndustryPackEngine;
import cn.ck.plm.base.pack.install.InstallReport;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台内置行业包自动安装 —— 启动时把平台级/默认行业内容装好，等价于"开箱即用的默认配置"。
 *
 * <h3>装什么</h3>
 * <ol>
 *   <li>{@code platform-base} —— 平台基础设施（生命周期状态字典、资源库树、其它行业阶段模板）；</li>
 *   <li>{@code electronics-core} —— 电子行业共用基础（软类型、分类树、编码/版本规则、认定阈值）；</li>
 *   <li>{@code smart-hardware-ipd} —— 默认行业变体（IPD 六阶段等）。首个行业包即它，
 *       平台默认体验＝智能硬件 IPD；租户可在引导时选择别的变体（electronics-hitech…）。</li>
 * </ol>
 * 三者都装到<b>平台租户</b>：平台级内容全租户共享；租户级变体由引导式初始化按租户安装。
 *
 * <h3>顺序</h3>
 * {@code @Order(1)}：<b>早于</b>各 {@code *Initializer}（类型/属性/页面布局等）—— 类型注册时要按 code 绑定
 * 编码/版本规则，规则与业务域必须先就位（原先由 {@code TypeDefinitionInitializer} /
 * {@code VersionRuleInitializer} / {@code NumberRuleInitializer} 等各自承担，P1 起改由本类装包承担，
 * 那些类已退场）。
 *
 * <h3>失败只告警</h3>
 * 包是数据：装不上不该让应用起不来（与各初始化器"失败只告警"的口径一致）。
 * 装不完的包状态会记成 PARTIAL，下次启动只补缺失项（幂等）。
 */
@Component
@Order(1)
public class IndustryPackBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IndustryPackBootstrap.class);

    /**
     * 随平台发布、启动自动安装的包。
     *
     * <p><b>只列顶层链</b>：{@code smart-hardware-ipd} 的 {@code basePack} 是 {@code electronics-core}，
     * 装它时引擎会先把父包装好 —— 若这里再单独列一次 electronics-core，同一个包在一次启动里会被装两遍：
     * 第二遍逐条判重全 SKIP，还会把明细表里第一遍的 INSERT 覆盖成 SKIP（排障时看错方向）。
     */
    private static final String[] BUILT_IN_PACKS = {
            "platform-base@1.0.0",      // 平台基础设施（不依赖任何包）
            "electronics-core@1.0.0"    // 电子行业共用基（分类树/器件参数/编码规则；变体包的公共父包）
    };

    private final IndustryPackEngine engine;

    public IndustryPackBootstrap(IndustryPackEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(String... args) {
        String previousTenant = TenantContext.getOrNull();
        TenantContext.set(TenantContext.PLATFORM_TENANT_OID);
        try {
            int installed = 0;
            int skipped = 0;
            for (String packId : BUILT_IN_PACKS) {
                try {
                    List<InstallReport> reports = engine.installChain(packId, TenantContext.PLATFORM_TENANT_OID,
                            IndustryPackEngine.noParams(), false);
                    InstallReport last = reports.isEmpty() ? null : reports.get(reports.size() - 1);
                    if (last == null) {
                        log.warn("行业包 {} 未安装（清单缺失？）", packId);
                        continue;
                    }
                    if (last.isSuccess()) {
                        installed++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    // 单个包装不上不影响其它包，也不影响启动
                    skipped++;
                    log.warn("行业包 {} 安装失败: {}", packId, e.getMessage());
                }
            }
            log.info("平台内置行业包就绪: 成功 {} 个, 有问题 {} 个（详见 /api/industry-packs/validate）",
                    installed, skipped);
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }
    }
}
