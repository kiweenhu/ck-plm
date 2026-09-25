/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CK-PLM 助手的 AI 配置（{@code plm.ai}）。
 *
 * <h3>为什么要支持"多种模型"</h3>
 * <p>一个部署里经常需要不止一个模型入口，而它们的差别不只是模型名：
 * <ul>
 *   <li>同一个网关下的不同模型（便宜的做日常问答、强的做复杂分析）；</li>
 *   <li>不同厂商/自建服务（凭据、登录路径、超时都不一样）；</li>
 *   <li>本地模型（Ollama / LM Studio）—— <b>根本没有凭据</b>。</li>
 * </ul>
 * 所以 {@code models} 里每一项都是<b>自包含</b>的「网关 + 凭据 + 模型名 + 路径」，
 * 而不是把"网关"和"模型名"拆成两层 —— 拆开之后就没法表达"两个模型走两个网关"。
 * 界面上可以直接切换，请求里带 {@code modelId} 指定用哪个。
 *
 * <h3>两种写法</h3>
 * <pre>
 * # 写法一：多模型（推荐，模板 sample 见 application-template.yml）
 * plm:
 *   ai:
 *     enabled: true
 *     default-model: workbuddy
 *     models:
 *       - id: workbuddy
 *         label: WorkBuddy
 *         base-url: https://gateway
 *         username: u
 *         password: p
 *         model: workbuddy
 *       - id: local
 *         base-url: http://localhost:11434/v1
 *         model: qwen2.5:14b
 *
 * # 写法二：只接一个模型时的简写（与项目里原有的配置方式一致）
 * plm:
 *   ai:
 *     enabled: true
 *     base-url: https://gateway
 *     api-key: sk-xxx
 *     model: gpt-4o-mini
 * </pre>
 *
 * <p>两种写法同时存在时 {@code models} 优先。简写会被合成成一个 {@code id=default} 的模型，
 * 所以上层（服务、接口、前端）只需要认 {@code models} 这一种结构。
 */
@Component
@ConfigurationProperties(prefix = "plm.ai")
public class AiProperties {

    /** 总开关。关掉时入口照常在，只是回答里说明"还没接上"——上线前也能先走通界面 */
    private boolean enabled = false;

    /** 不指定模型时用哪个（{@code models[].id}）；留空取第一个可用模型 */
    private String defaultModel;

    /** 工具调用轮次上限：模型偶尔会反复查同一个工具，兜住它（超了强制要文字回答） */
    private int maxToolRounds = 4;

    /** 单次请求超时（秒）；单个模型可用 {@code models[].timeout-seconds} 覆盖 */
    private int timeoutSeconds = 60;

    /** 可用的模型清单（界面上的下拉就是它） */
    private List<AiModel> models = new ArrayList<>();

    // ==================== 简写（只接一个模型时用） ====================
    private String baseUrl;
    private String username;
    private String password;
    private String apiKey;
    private String model;
    private String loginPath;
    private String chatPath;

    /**
     * 最终生效的模型清单。
     *
     * <p>只保留<b>可用</b>的项（{@link AiModel#isUsable()}）—— 声明了但没填地址、
     * 或显式停用的，不进入清单：它们出现在界面下拉里只会让人选到一个必然失败的选项。
     * 这样"同一份配置分环境部署"（模板里三条样例、某个环境只填一条）也不会互相干扰。
     */
    public List<AiModel> resolvedModels() {
        if (models != null && !models.isEmpty()) {
            List<AiModel> result = new ArrayList<>();
            for (AiModel item : models) {
                if (item != null && item.isUsable()) {
                    result.add(item);
                }
            }
            return result;
        }
        if (isBlank(baseUrl)) {
            return List.of();
        }
        AiModel single = new AiModel();
        single.setId("default");
        single.setBaseUrl(baseUrl);
        single.setUsername(username);
        single.setPassword(password);
        single.setApiKey(apiKey);
        single.setModel(model);
        single.setLoginPath(loginPath);
        single.setChatPath(chatPath);
        return List.of(single);
    }

    /**
     * 按 id 取模型；{@code id} 为空时取默认模型。
     *
     * <p>找不到返回 {@code null}，由调用方给出"没有这个模型"的可读提示 ——
     * <b>不</b>静默换成另一个模型回答：那样用户会以为"换模型生效了"，实际没有。
     */
    public AiModel find(String id) {
        List<AiModel> list = resolvedModels();
        if (list.isEmpty()) {
            return null;
        }
        String wanted = isBlank(id) ? defaultModel : id;
        if (isBlank(wanted)) {
            return list.get(0);
        }
        for (AiModel item : list) {
            if (wanted.trim().equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    /** 默认模型的 id（前端下拉的默认选中项）；没有可用模型时为 {@code null} */
    public String defaultModelId() {
        AiModel item = find(null);
        return item == null ? null : item.getId();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ==================== 一个模型入口 ====================

    /**
     * 一个模型入口：网关地址 + 凭据 + 模型名 + 路径。
     *
     * <p>这三样构成一次调用所需的<b>全部</b>信息，所以放在一起：拆成"全局网关 + 模型清单"
     * 就没法表达"两个模型走两个网关"。
     */
    public static class AiModel {

        /** 单个模型的开关：临时停用某个模型时不用删配置；模板里的样例默认关着，填好凭据再打开 */
        private boolean enabled = true;

        /** 唯一 id：前端选模型、日志定位都用它 */
        private String id;

        /** 界面上显示的名字；不填就用模型名、再退到 id */
        private String label;

        private String baseUrl;

        /** 认证三选一：api-key（优先）→ 账号密码登录换令牌 → 都不要（本地模型） */
        private String apiKey;
        private String username;
        private String password;

        /** 请求体里的 model 字段；不填就用 id */
        private String model;

        private String loginPath;
        private String chatPath;

        /** 单模型超时覆盖（秒）；不填用全局 */
        private Integer timeoutSeconds;

        private static final String DEFAULT_LOGIN_PATH = "/auth/login";
        private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";

        /** 是否可用：开关打开且填了网关地址 */
        public boolean isUsable() {
            return enabled && baseUrl != null && !baseUrl.trim().isEmpty();
        }

        /** 界面显示名 */
        public String displayLabel() {
            if (label != null && !label.trim().isEmpty()) {
                return label.trim();
            }
            if (model != null && !model.trim().isEmpty()) {
                return model.trim();
            }
            return id == null ? "未命名模型" : id;
        }

        /** 请求体里的模型名：没单独填就用 id，省一次重复配置 */
        public String requestModel() {
            return model != null && !model.trim().isEmpty() ? model.trim() : id;
        }

        public String effectiveLoginPath() {
            return loginPath == null || loginPath.trim().isEmpty() ? DEFAULT_LOGIN_PATH : loginPath.trim();
        }

        public String effectiveChatPath() {
            return chatPath == null || chatPath.trim().isEmpty() ? DEFAULT_CHAT_PATH : chatPath.trim();
        }

        /** 认证方式（给前端显示，也是排障时第一个要看的东西） */
        public String authMode() {
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                return "api-key";
            }
            if (username != null && !username.trim().isEmpty()) {
                return "password";
            }
            return "none";
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getLoginPath() { return loginPath; }
        public void setLoginPath(String loginPath) { this.loginPath = loginPath; }

        public String getChatPath() { return chatPath; }
        public void setChatPath(String chatPath) { this.chatPath = chatPath; }

        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    // ==================== getter / setter ====================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public int getMaxToolRounds() { return maxToolRounds; }
    public void setMaxToolRounds(int maxToolRounds) { this.maxToolRounds = maxToolRounds; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public List<AiModel> getModels() { return models; }
    public void setModels(List<AiModel> models) { this.models = models; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getLoginPath() { return loginPath; }
    public void setLoginPath(String loginPath) { this.loginPath = loginPath; }

    public String getChatPath() { return chatPath; }
    public void setChatPath(String chatPath) { this.chatPath = chatPath; }
}
