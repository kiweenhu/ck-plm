/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import java.util.ArrayList;
import java.util.List;

/**
 * 安装结果 —— 一条资源（或一条资源里的一个条目）的落地明细。
 *
 * <p>它是三件事的共同载体：
 * <ol>
 *   <li><b>对账</b>：装了多少、跳过多少、失败几条（写入 {@code ck_industry_pack_resource}）；</li>
 *   <li><b>引导页的"预演/播放"</b>：前端按 {@code resourceType} 分组把结果逐条"播放"出来
 *       （"类型树：新增 21，跳过 0"）；</li>
 *   <li><b>排障</b>：{@code message} 带上失败原因，{@code targetTable} 带上落到哪张表。</li>
 * </ol>
 *
 * <p>约定：{@code action} 只有四种取值，前端与报表据此着色（新增/跳过/更新/失败）。
 */
public class InstallResult {

    /** 落地动作 */
    public enum Action {
        /** 新建 */
        INSERT,
        /** 已存在、按幂等契约跳过（不覆盖用户改过的值） */
        SKIP,
        /** 更新（仅当资源显式声明"可覆盖"时才用，例如包升级） */
        UPDATE,
        /** 失败（单条失败不阻断整体） */
        FAILED
    }

    /** 资源类型（与资源声明的 type 一致） */
    private String resourceType;
    /** 幂等键（业务键），重复安装时据此判 SKIP；同一资源内多条时用 "类型#子键" 形式 */
    private String resourceKey;
    /** 落地动作 */
    private Action action = Action.INSERT;
    /** 落到哪张表 */
    private String targetTable;
    /** 该资源的行 oid（可用于后续更新） */
    private String targetOid;
    /** 示例数据专用：本次创建的业务对象 oid（"清理示例"据此精确回滚） */
    private String createdObjectOid;
    /** 失败原因 / 跳过说明 / 提示 */
    private String message;
    /** 单条内容指纹 */
    private String contentHash;
    /**
     * 这条 SKIP 是否表示"已就绪"（本租户已有可用内容：平台共享可见 / 已存在）。
     *
     * <p>SKIP 有两种含义，界面必须分开显示：
     * <ul>
     *   <li>{@code true}（默认）：已有可用内容，不需要写 —— 显示"已就绪"；</li>
     *   <li>{@code false}：预期性跳过 / 前置未就绪 / 无事可做（如预演时页面布局还没有可套用的类型、
     *       引用的依赖尚未装入）—— 显示"<b>跳过</b>"。把它画成"已就绪"会让管理员以为东西已经在那儿了。</li>
     * </ul>
     */
    private boolean ready = true;

    public InstallResult() {
    }

    public InstallResult(String resourceType, String resourceKey, Action action) {
        this.resourceType = resourceType;
        this.resourceKey = resourceKey;
        this.action = action;
    }

    // ==================== 工厂方法（让 Installer 的代码读起来像句子） ====================

    public static InstallResult inserted(String type, String key) {
        return new InstallResult(type, key, Action.INSERT);
    }

    public static InstallResult skipped(String type, String key, String why) {
        InstallResult r = new InstallResult(type, key, Action.SKIP);
        r.setMessage(why);
        return r;
    }

    /**
     * 跳过（<b>非"已就绪"</b>语义）：前置未就绪 / 无事可做 / 预期内不写。
     *
     * <p>不是失败（不进 FAILED 统计、不阻断装包），但界面应显示为"跳过"而不是"已就绪" ——
     * 见 {@link #isReady()}。凡"跳过是因为东西还没到时候/没有可做的"，都用这个工厂。
     */
    public static InstallResult skippedPending(String type, String key, String why) {
        InstallResult r = skipped(type, key, why);
        r.setReady(false);
        return r;
    }

    public static InstallResult updated(String type, String key) {
        return new InstallResult(type, key, Action.UPDATE);
    }

    public static InstallResult failed(String type, String key, String why) {
        InstallResult r = new InstallResult(type, key, Action.FAILED);
        r.setMessage(why);
        return r;
    }

    /** 链式设置（少写几行样板） */
    public InstallResult on(String table, String oid) {
        this.targetTable = table;
        this.targetOid = oid;
        return this;
    }

    public InstallResult asObject(String objectOid) {
        this.createdObjectOid = objectOid;
        return this;
    }

    public InstallResult why(String message) {
        this.message = message;
        return this;
    }

    public InstallResult hash(String hash) {
        this.contentHash = hash;
        return this;
    }

    /** 汇总一组结果的计数（{@code [insert, skip, update, failed]}） */
    public static int[] count(List<InstallResult> results) {
        int[] c = new int[4];
        if (results == null) {
            return c;
        }
        for (InstallResult r : results) {
            switch (r.getAction()) {
                case INSERT: c[0]++; break;
                case SKIP:   c[1]++; break;
                case UPDATE: c[2]++; break;
                case FAILED: c[3]++; break;
                default: break;
            }
        }
        return c;
    }

    /** 只保留失败项（给报告用） */
    public static List<InstallResult> failures(List<InstallResult> results) {
        List<InstallResult> failures = new ArrayList<>();
        if (results != null) {
            for (InstallResult r : results) {
                if (r.getAction() == Action.FAILED) {
                    failures.add(r);
                }
            }
        }
        return failures;
    }

    // ==================== Getter / Setter ====================

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

    public String getTargetOid() { return targetOid; }
    public void setTargetOid(String targetOid) { this.targetOid = targetOid; }

    public String getCreatedObjectOid() { return createdObjectOid; }
    public void setCreatedObjectOid(String createdObjectOid) { this.createdObjectOid = createdObjectOid; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    /** 是否"已就绪"（true=已有可用内容）；false=预期性跳过，界面显示"跳过" */
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    @Override
    public String toString() {
        return "[" + action + "] " + resourceType + ":" + resourceKey
                + (message == null ? "" : " (" + message + ")");
    }
}
