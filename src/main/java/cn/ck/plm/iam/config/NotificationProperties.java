/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知模式配置（{@code plm.notification}）—— <b>全系统统一</b>的通知渠道与企业集成凭据。
 *
 * <h3>为什么放在这里，而不是流程模板里</h3>
 * <p>通知渠道的凭据是<b>企业集成</b>：一个部署里只有一台邮件服务器、一个 OA 接口、一套飞书应用凭据。
 * 早先的设计把它放在流程模板的属性里（每个流程各填一遍 SMTP 密码），
 * 于是：改一次密码要改所有流程；凭据随模板 JSON 复制/导出到处跑；同一家公司有几套配置谁也说不清。
 *
 * <p>现在：<b>渠道与凭据在这里配一次，流程侧只读</b> —— 流程模板最多声明"本流程允许用哪些通道"，
 * 且只能从 {@link #getChannels()} 里选。这样"通知走什么方式"这件事全系统只有一个答案。
 *
 * <h3>配置样例（完整样例见 application-template.yml）</h3>
 * <pre>
 * plm:
 *   notification:
 *     enabled: true
 *     channels: [CK_PLM, EMAIL]
 *     email:
 *       host: smtp.example.com
 *       port: 465
 *       sender: noreply@example.com
 *       username: ${PLM_NOTIFY_EMAIL_USERNAME:}
 *       password: ${PLM_NOTIFY_EMAIL_PASSWORD:}
 *       ssl: true
 * </pre>
 *
 * <p>凭据建议一律走环境变量：application.yml 受 git 跟踪，写死会随提交泄露。
 */
@Component
@ConfigurationProperties(prefix = "plm.notification")
public class NotificationProperties {

    /** 站内信（平台自带，无需任何集成凭据） */
    public static final String CHANNEL_CK_PLM = "CK_PLM";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_OA = "OA";
    public static final String CHANNEL_FEISHU = "FEISHU";
    public static final String CHANNEL_DINGTALK = "DINGTALK";
    public static final String CHANNEL_WECOM = "WECOM";

    /** 渠道显示名（后端也要：接口返回值与失败信息都要给人看，不能只给编码） */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(CHANNEL_CK_PLM, "CK-PLM（站内）");
        LABELS.put(CHANNEL_EMAIL, "邮件系统");
        LABELS.put(CHANNEL_OA, "OA 系统");
        LABELS.put(CHANNEL_FEISHU, "飞书");
        LABELS.put(CHANNEL_DINGTALK, "钉钉");
        LABELS.put(CHANNEL_WECOM, "企业微信");
    }

    /**
     * 通知总开关。关掉后系统产生的自动通知（待办 / 转办 / 流程结束 …）不再落库，
     * 但用户自己"发布公告"这类显式动作不受影响（那是人点的，不是系统自动发的）。
     */
    private boolean enabled = true;

    /**
     * 系统启用的通知渠道（顺序即发送优先级）。
     *
     * <p>默认只启用站内信：新部署没配任何企业集成时也能用，不该因为"没配邮件"就没有通知。
     */
    private List<String> channels = new ArrayList<>(List.of(CHANNEL_CK_PLM));

    private Email email = new Email();
    private Oa oa = new Oa();
    private Im feishu = new Im();
    private Im dingtalk = new Im();
    private Im wecom = new Im();

    // ==================== 查询 ====================

    /** 全部渠道编码（界面要显示"未启用的"并置灰，所以需要全量而不是只给启用的） */
    public static List<String> allChannels() {
        return new ArrayList<>(LABELS.keySet());
    }

    public static String labelOf(String code) {
        return LABELS.getOrDefault(normalize(code), code == null ? "" : code);
    }

    /** 选中的渠道（去重、大写、按配置顺序） */
    public List<String> selectedChannels() {
        List<String> result = new ArrayList<>();
        for (String channel : channels != null ? channels : List.<String>of()) {
            String code = normalize(channel);
            if (LABELS.containsKey(code) && !result.contains(code)) {
                result.add(code);
            }
        }
        return result;
    }

    /** 该渠道是否被系统启用（只看 enabled 与配置里的声明，不看凭据是否齐备） */
    public boolean isChannelSelected(String channel) {
        return selectedChannels().contains(normalize(channel));
    }

    public boolean isMasterEnabled() {
        return enabled;
    }

    /**
     * 该渠道的必填项还缺什么（{@code null} = 配好了）。
     *
     * <p>没配齐的渠道即便被声明为启用也发不出去，所以"能不能用"要连凭据一起看
     * （见 {@code NotificationChannelService#isUsable}）。
     */
    public String missingConfigOf(String channel) {
        switch (normalize(channel)) {
            case CHANNEL_CK_PLM:
                return null;   // 平台自带，无凭据
            case CHANNEL_EMAIL:
                return firstMissing(email == null ? null : email.host, "邮件服务器地址",
                        email == null ? null : email.sender, "发件人");
            case CHANNEL_OA:
                return firstMissing(oa == null ? null : oa.apiUrl, "OA 接口地址");
            case CHANNEL_FEISHU:
                return firstMissing(feishu == null ? null : feishu.webhook, "群机器人 Webhook",
                        feishu == null ? null : feishu.appId, "应用 App ID");
            case CHANNEL_DINGTALK:
                return firstMissing(dingtalk == null ? null : dingtalk.webhook, "群机器人 Webhook");
            case CHANNEL_WECOM:
                return firstMissing(wecom == null ? null : wecom.webhook, "群机器人 Webhook",
                        wecom == null ? null : wecom.corpId, "企业 Corp ID");
            default:
                return "未知渠道";
        }
    }

    /** 两个值里第一个为空的，返回"缺 X"；都填了返回 null */
    private static String firstMissing(String first, String firstLabel, String... rest) {
        if (isBlank(first)) {
            return "缺「" + firstLabel + "」";
        }
        for (int i = 0; i + 1 < rest.length; i += 2) {
            if (isBlank(rest[i])) {
                return "缺「" + rest[i + 1] + "」";
            }
        }
        return null;
    }

    private static String normalize(String channel) {
        return channel == null ? "" : channel.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ==================== Getter / Setter ====================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getChannels() { return channels; }
    public void setChannels(List<String> channels) { this.channels = channels; }

    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }

    public Oa getOa() { return oa; }
    public void setOa(Oa oa) { this.oa = oa; }

    public Im getFeishu() { return feishu; }
    public void setFeishu(Im feishu) { this.feishu = feishu; }

    public Im getDingtalk() { return dingtalk; }
    public void setDingtalk(Im dingtalk) { this.dingtalk = dingtalk; }

    public Im getWecom() { return wecom; }
    public void setWecom(Im wecom) { this.wecom = wecom; }

    /** 邮件渠道凭据 */
    public static class Email {
        private String host;
        private Integer port = 465;
        private String sender;
        private String username;
        private String password;
        private boolean ssl = true;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
    }

    /** OA 渠道凭据（自建 RESTful 接口） */
    public static class Oa {
        private String apiUrl;
        /** NONE / BASIC / TOKEN */
        private String authType = "TOKEN";
        private String username;
        private String password;
        private String token;

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getAuthType() { return authType; }
        public void setAuthType(String authType) { this.authType = authType; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    /** 即时通讯渠道凭据（飞书 / 钉钉 / 企业微信）：两种接入方式，填哪种用哪种 */
    public static class Im {
        /** 群机器人 Webhook */
        private String webhook;
        private String secret;
        /** 自建应用凭据 */
        private String appId;
        private String appSecret;
        private String corpId;
        private String agentId;

        public String getWebhook() { return webhook; }
        public void setWebhook(String webhook) { this.webhook = webhook; }

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }

        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

        public String getCorpId() { return corpId; }
        public void setCorpId(String corpId) { this.corpId = corpId; }

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
    }
}
