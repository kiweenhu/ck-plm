/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.dto;

/**
 * 通知渠道视图 —— 「系统支持哪些渠道、哪些真的能用」。
 *
 * <p>给设计器与管理界面用：设计器只能从 {@code usable=true} 的渠道里挑；
 * 不可用的要显示原因（{@code missing}），而不是让人配完再运行期失败。
 */
public class NotificationChannelVO {

    /** 渠道编码（CK_PLM / EMAIL / OA / FEISHU / DINGTALK / WECOM） */
    private String code;

    /** 显示名（CK-PLM（站内） / 邮件系统 …） */
    private String label;

    /** 是否被系统启用（配置里声明了） */
    private boolean enabled;

    /** 现在能否真的发出去：启用 + 凭据齐备（站内信无需凭据） */
    private boolean usable;

    /** 不能用的原因（如「缺『邮件服务器地址』」）；可用时为 null */
    private String missing;

    public NotificationChannelVO() {
    }

    public NotificationChannelVO(String code, String label, boolean enabled, boolean usable, String missing) {
        this.code = code;
        this.label = label;
        this.enabled = enabled;
        this.usable = usable;
        this.missing = missing;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isUsable() { return usable; }
    public void setUsable(boolean usable) { this.usable = usable; }

    public String getMissing() { return missing; }
    public void setMissing(String missing) { this.missing = missing; }
}
