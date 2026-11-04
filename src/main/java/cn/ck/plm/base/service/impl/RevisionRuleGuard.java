/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.service.api.VersionRuleService;
import cn.ck.plm.iam.service.api.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 修订前置校验 —— 当前大版本是否已是版本规则允许的<b>最后一个</b>。
 *
 * <h3>为什么必须挡在这里</h3>
 * <p>版本规则用尽时 {@code getNextRevision} 返回 null，而各宿主原先都把 null 回退成
 * {@code char+1} —— 于是 {@code (A,B,C)} 的规则在 C 之上还能长出 D：界面上版本号"看着正常"，
 * 实际已经脱离规则，且没有任何人被告知该改规则。更糟的是修订出来的 D 版会被后续检出/检入
 * 当成合法基线继续使用，等到要追溯"这个版本号哪来的"时已经无从解释。
 *
 * <p>统一在 base 层做，是因为 Part / Document / EngineeringDocument 以及流程
 * {@code object.promote} 最终都走各宿主的 {@code newViewVersion}：口径必须一处定义。
 *
 * <p>拦截时：① 抛 {@link IllegalStateException}（统一入口映射 409，修订不会发生）；
 * ② 站内通知本租户管理员去「业务配置 → 版本规则」补充后续大版本。通知失败不影响拦截本身
 * —— 阻止错误数据产生是第一位的。
 */
@Component
public class RevisionRuleGuard {

    private static final Logger log = LoggerFactory.getLogger(RevisionRuleGuard.class);

    /** 通知类型：版本规则（前端按此给图标与跳转） */
    public static final String NOTIFY_TYPE = "VERSION_RULE";

    private final VersionRuleService versionRuleService;
    private final NotificationService notificationService;

    public RevisionRuleGuard(VersionRuleService versionRuleService,
                             NotificationService notificationService) {
        this.versionRuleService = versionRuleService;
        this.notificationService = notificationService;
    }

    /**
     * 便捷重载：用「编号 + 名称」拼对象展示名（与列表口径一致）。
     */
    public void ensureRevisionAllowed(String typeCode, String currentRevision, String number, String name) {
        String label = ((number != null ? number : "") + " " + (name != null ? name : "")).trim();
        ensureRevisionAllowed(typeCode, currentRevision, label);
    }

    /**
     * 校验当前版本能否修订；不能则通知租户管理员并抛异常。
     *
     * <p>取不到类型绑定 / 规则解析异常时<b>不阻断</b>：与既有"没有规则就按 char+1"的口径一致，
     * 配置缺失不该让业务动作停在一条与规则无关的错误上。
     *
     * @param typeCode        类型编码（解析其绑定的版本规则）
     * @param currentRevision 当前大版本（如 A）
     * @param objectLabel     对象展示名（写进通知与错误信息，便于管理员定位）
     * @throws IllegalStateException 已是最后一个大版本
     */
    public void ensureRevisionAllowed(String typeCode, String currentRevision, String objectLabel) {
        if (typeCode == null || typeCode.trim().isEmpty() || currentRevision == null) {
            return;
        }

        String ruleCode;
        boolean last;
        try {
            ruleCode = versionRuleService.resolveVersionRuleCode(typeCode);
            if (ruleCode == null) {
                return;
            }
            last = versionRuleService.isLastRevision(ruleCode, currentRevision);
        } catch (Exception e) {
            log.warn("修订前置校验跳过（版本规则解析失败）: typeCode={}, revision={}, error={}",
                    typeCode, currentRevision, e.getMessage());
            return;
        }
        if (!last) {
            return;
        }

        String message = String.format(
                "对象「%s」当前大版本 %s 已是版本规则「%s」的最后一个大版本，无法修订。"
                        + "请到「业务配置 → 版本规则」补充后续大版本序列后再试。",
                objectLabel != null && !objectLabel.trim().isEmpty() ? objectLabel : "(未命名对象)",
                currentRevision, ruleCode);
        notifyTenantAdmins(ruleCode, message);
        throw new IllegalStateException(message);
    }

    /** 通知本租户管理员；失败只记日志 —— 不能因为通知发不出去就放行修订 */
    private void notifyTenantAdmins(String ruleCode, String message) {
        try {
            int sent = notificationService.sendToTenantAdmins(
                    "版本规则需要更新：已到最后一个大版本",
                    message,
                    NOTIFY_TYPE, NOTIFY_TYPE, ruleCode);
            if (sent == 0) {
                log.warn("版本规则已用尽，但未找到可通知的租户管理员: ruleCode={}", ruleCode);
            }
        } catch (Exception e) {
            log.warn("通知租户管理员失败（修订仍被阻止）: ruleCode={}, error={}", ruleCode, e.getMessage());
        }
    }
}
