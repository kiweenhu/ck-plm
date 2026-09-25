/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ai.service.impl;

import cn.ck.plm.ai.config.AiProperties;
import cn.ck.plm.ai.config.AiProperties.AiModel;
import cn.ck.plm.ai.service.api.AiChatService;
import cn.ck.plm.ai.support.PlmTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CK-PLM 助手实现 —— OpenAI 兼容客户端 + 工具调用循环。
 *
 * <h3>模型从哪来</h3>
 * <p>全部配置见 {@link AiProperties}（{@code plm.ai}）：可以有<b>多个</b>模型入口，每个自带
 * 网关地址、凭据、模型名与路径。本类不做任何"选模型"的猜测 —— 请求里带 {@code modelId}
 * 就用它，没带就用默认模型，<b>指定了但不存在就明确报错</b>（静默换一个模型会让人以为切换生效了）。
 *
 * <h3>几个刻意的取舍</h3>
 * <ul>
 *   <li><b>不引新依赖</b>：用 JDK 自带 {@link HttpClient}，JSON 走已有的 Jackson ——
 *       为一个可选功能加 SDK 不划算，出错面也更大。</li>
 *   <li><b>令牌只放内存</b>：账号密码登录换来的令牌缓存在服务实例里（10 分钟），
 *       按模型 id 分开缓存（各自的网关与凭据互不影响）；不落库、不进日志；401 时自动重登一次。</li>
 *   <li><b>允许无凭据</b>：本地模型（Ollama / LM Studio）不需要 key，所以"凭据"三选一，
 *       一个都没有也能调 —— 只要求填了 {@code base-url}。</li>
 *   <li><b>工具调用有轮次上限</b>：模型偶尔会反复查同一个工具，{@code max-tool-rounds}
 *       兜住它，最后一轮强制要文字回答（否则用户会一直等）。</li>
 *   <li><b>未配置不是错误</b>：返回一句可读的说明（含要填哪些配置），前端照常能聊，
 *       只是回答变成"我还没接上"。这样上线前也能先走通界面。</li>
 * </ul>
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    /** 令牌有效期：到点主动重登，避免"聊到一半突然 401" */
    private static final long TOKEN_TTL_MS = 10 * 60 * 1000L;

    /** 配了模型但不可用时的说明：把"要改哪个配置项"写清楚，比"服务不可用"有用 */
    private static final String NOT_CONFIGURED_TEXT =
            "助手还没接上可用模型。请在 application.yml 的 plm.ai.models 里检查：每一项都要填 base-url，"
            + "若该行写了 enabled: false 要先打开（本地模型可不要凭据），重启后即可使用。";

    /**
     * 总开关关着时的说明。
     *
     * <p>刻意与"没配模型"分开说 —— 这两种情况要改的键完全不同，混成一句会让人
     * 明明配好了模型却一直被告知"去配置"，白折腾一圈（见 {@link #unavailableHint()}）。
     */
    private static final String DISABLED_TEXT =
            "助手的总开关还没打开：请把 application.yml 里 plm.ai.enabled 改成 true"
            + "（也可以用环境变量 PLM_AI_ENABLED=true），重启后端即可。";

    /** 系统提示词：说清"能查什么"与"别编数据"，这是回答质量的主要来源 */
    private static final String SYSTEM_PROMPT =
            "你是 CK-PLM（产品生命周期管理系统）里的助手。"
            + "回答用户关于系统数据的问题时，必须先用提供的工具查询真实数据，再依据查询结果回答；"
            + "工具没查到就说没查到，绝对不要凭常识编造编码、名称、状态或人名。"
            + "回答用简体中文，简洁、口语化，必要时用短列表；不要输出 JSON 原文，"
            + "也不要说\"根据工具返回\"这种实现细节。若问题与 CK-PLM 数据无关，可以直接回答。";

    private final PlmTools tools;
    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** 模型 id → 登录令牌（每个模型各自一份；不落库、不打印） */
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    /** 令牌 + 取得时间 */
    private record Token(String value, long at) {
    }

    public AiChatServiceImpl(PlmTools tools, AiProperties props, ObjectMapper objectMapper) {
        this.tools = tools;
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Map<String, Object> status() {
        List<AiModel> models = props.resolvedModels();
        List<Map<String, Object>> items = new ArrayList<>(models.size());
        for (AiModel model : models) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", model.getId());
            item.put("label", model.displayLabel());
            item.put("model", model.requestModel());
            item.put("authMode", model.authMode());
            items.add(item);
        }
        AiModel active = props.find(null);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", props.isEnabled());
        map.put("configured", props.isEnabled() && !models.isEmpty());
        // state/hint 给前端直接用：能用了是哪种原因不能用，说得明白一点。
        // 前端不再自己拼文案 —— 文案只在后端一处维护，改配置项名字时不会漏改前端。
        map.put("state", stateOf());
        map.put("hint", isReady() ? null : unavailableHint());
        map.put("defaultModel", props.defaultModelId());
        map.put("models", items);
        // 顶层 model/authMode 保留：说的是"默认模型"。只有一个模型时前端直接显示一个标签就够了
        map.put("model", active == null ? null : active.requestModel());
        map.put("authMode", active == null ? "none" : active.authMode());
        return map;
    }

    @Override
    public Map<String, Object> chat(String message, List<Map<String, String>> history, String modelId) {
        if (!isReady()) {
            return answer(unavailableHint(), List.of(), false);
        }
        AiModel target = props.find(modelId);
        if (target == null) {
            // 指定了一个不存在/被停用的模型：说清楚，不悄悄换一个回答
            return answer("没有可用的模型「" + (modelId == null ? "" : modelId.trim())
                    + "」：可能没填 base-url，或被 enabled: false 停用了。请检查 plm.ai.models。",
                    List.of(), false);
        }
        if (message == null || message.trim().isEmpty()) {
            return answer("请说说你想查什么？", List.of(), true);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg("system", SYSTEM_PROMPT));
        appendHistory(messages, history);
        messages.add(msg("user", message.trim()));

        List<String> usedTools = new ArrayList<>();
        try {
            for (int round = 0; round <= props.getMaxToolRounds(); round++) {
                Map<String, Object> choice = firstChoice(postChat(target, messages));
                if (choice == null) {
                    return answer("模型没有返回内容，请稍后再试。", usedTools, true);
                }
                List<Map<String, Object>> toolCalls = toolCalls(choice);
                boolean lastRound = round == props.getMaxToolRounds();
                if (toolCalls.isEmpty() || lastRound) {
                    String content = str(choice.get("content"));
                    return answer(content.isEmpty() ? "（模型没有给出文字回答）" : content, usedTools, true);
                }
                // 带工具调用的助手消息必须原样放回上下文，否则下一轮协议不成立
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", choice.get("content"));
                assistantMsg.put("tool_calls", toolCalls);
                messages.add(assistantMsg);

                for (Map<String, Object> call : toolCalls) {
                    String name = callName(call);
                    usedTools.add(name);
                    String result = tools.execute(name, callArgs(call));
                    messages.add(toolMsg(callId(call), result));
                    log.info("AI 调用工具: model={} name={} 参数={} 结果长度={}", target.getId(), name,
                            callArgs(call), result == null ? 0 : result.length());
                }
            }
            return answer("（已达到工具调用轮次上限，请把问题拆小一点再问）", usedTools, true);
        } catch (Exception e) {
            log.warn("AI 对话失败: model={} error={}", target.getId(), e.getMessage());
            return answer("调用大模型失败：" + e.getMessage(), usedTools, true);
        }
    }

    // ==================== 传输层（换网关只改这几个方法） ====================

    /** 发一轮对话请求（带工具声明） */
    private Map<String, Object> postChat(AiModel model, List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.requestModel());
        body.put("messages", messages);
        body.put("tools", tools.definitions());
        body.put("tool_choice", "auto");
        body.put("temperature", 0.2);
        Map<String, Object> json = readJson(post(model, model.effectiveChatPath(), body, true));
        return json == null ? Map.of() : json;
    }

    /** POST + 认证；401 时用账号密码重登一次再试 */
    private String post(AiModel model, String path, Map<String, Object> body, boolean withAuth) {
        String raw = doPost(model, path, body, withAuth);
        if (raw == null && withAuth && isNotBlank(model.getUsername())) {
            tokens.remove(model.getId());            // 令牌可能过期：重登后重试一次
            raw = doPost(model, path, body, true);
        }
        if (raw == null) {
            throw new IllegalStateException("模型「" + model.getId()
                    + "」认证失败（401）：账号密码没能换成有效令牌");
        }
        return raw;
    }

    private String doPost(AiModel model, String path, Map<String, Object> body, boolean withAuth) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(join(model.getBaseUrl(), path)))
                    .timeout(Duration.ofSeconds(timeoutOf(model)))
                    .header("Content-Type", "application/json");
            // 只在需要认证时才去取认证头：登录请求自己也是"不需要认证"的，
            // 无条件取会变成 login → post → authHeader → login 的无限递归（栈溢出）。
            if (withAuth) {
                String auth = authHeader(model);
                if (auth != null) {
                    builder.header("Authorization", auth);
                }
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401) {
                // 有账号密码还能重登一次再试；只能返回 null 让 post() 去重试
                if (isNotBlank(model.getUsername())) {
                    log.warn("AI 服务返回 401（将重新登录后重试）: model={} path={}", model.getId(), path);
                    return null;
                }
                // 没有账号密码可重登 —— 这就是"凭据不被接受"。直说认证问题，
                // 别让人以为是 base-url 写错了（原来是这么报的，白查半天）
                throw new IllegalStateException("模型「" + model.getId() + "」认证失败（401）："
                        + (isNotBlank(model.getApiKey())
                            ? "api-key 可能无效或已过期" : "该网关需要凭据，请配置 api-key 或账号密码")
                        + "。服务端返回：" + truncate(response.body()));
            }
            if (status / 100 != 2) {
                throw new IllegalStateException("AI 服务返回 " + status + "：" + truncate(response.body()));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** 单模型超时覆盖优先，其次全局 */
    private int timeoutOf(AiModel model) {
        return model.getTimeoutSeconds() == null ? props.getTimeoutSeconds() : model.getTimeoutSeconds();
    }

    /** 认证头：优先 api-key，其次账号密码登录，都没有就不带（本地模型） */
    private String authHeader(AiModel model) {
        if (isNotBlank(model.getApiKey())) {
            return "Bearer " + model.getApiKey().trim();
        }
        if (isNotBlank(model.getUsername())) {
            return "Bearer " + login(model);
        }
        return null;
    }

    /** 账号密码登录换令牌（按模型缓存 10 分钟） */
    private synchronized String login(AiModel model) {
        Token cached = tokens.get(model.getId());
        if (cached != null && System.currentTimeMillis() - cached.at() < TOKEN_TTL_MS) {
            return cached.value();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", model.getUsername());
        body.put("password", model.getPassword());
        String raw = post(model, model.effectiveLoginPath(), body, false);
        Map<String, Object> json = readJson(raw);
        String found = firstString(json, "token", "access_token", "accessToken");
        if (found == null && json.get("data") instanceof Map<?, ?> data) {
            found = firstString(cast(data), "token", "access_token", "accessToken");
        }
        if (found == null) {
            throw new IllegalStateException("登录 AI 服务未拿到令牌（响应：" + truncate(raw) + "）");
        }
        tokens.put(model.getId(), new Token(found, System.currentTimeMillis()));
        log.info("AI 服务登录成功: model={} user={}", model.getId(), model.getUsername());
        return found;
    }

    /** 从 OpenAI 兼容响应里取第一个 choice */
    private Map<String, Object> firstChoice(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return null;
        }
        Object message = choice.get("message");
        if (message instanceof Map<?, ?> msg) {
            return cast(msg);
        }
        // 有些网关把文本放在 choices[0].text
        Object text = choice.get("text");
        return text == null ? null : Map.of("content", text);
    }

    /** 取 tool_calls（OpenAI 格式：[{id, type, function:{name, arguments}}]） */
    private List<Map<String, Object>> toolCalls(Map<String, Object> message) {
        Object calls = message.get("tool_calls");
        if (!(calls instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(cast(map));
            }
        }
        return result;
    }

    private String callId(Map<String, Object> call) {
        String id = str(call.get("id"));
        return id.isEmpty() ? "call_" + Math.abs(call.hashCode()) : id;
    }

    private String callName(Map<String, Object> call) {
        Object fn = call.get("function");
        if (fn instanceof Map<?, ?> map) {
            return str(cast(map).get("name"));
        }
        // 少数网关放在 call.name
        return str(call.get("name"));
    }

    /** 工具参数：OpenAI 给的是 JSON 字符串（少数网关直接给对象） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callArgs(Map<String, Object> call) {
        Object fn = call.get("function");
        Object raw = fn instanceof Map<?, ?> map ? cast(map).get("arguments") : call.get("arguments");
        if (raw instanceof Map<?, ?> map) {
            return cast(map);
        }
        if (raw instanceof String text && !text.trim().isEmpty()) {
            try {
                return objectMapper.readValue(text, Map.class);
            } catch (Exception e) {
                log.debug("工具参数不是合法 JSON，按空参数处理: {}", text);
            }
        }
        return Map.of();
    }

    // ==================== 小工具 ====================

    /** 能不能用：总开关打开 + 至少有一个可用模型 */
    private boolean isReady() {
        return props.isEnabled() && !props.resolvedModels().isEmpty();
    }

    /** 不能用的状态（前端据此换标题）：disabled=总开关关着 / no-models=没有可用模型 */
    private String stateOf() {
        if (!props.isEnabled()) {
            return "disabled";
        }
        return props.resolvedModels().isEmpty() ? "no-models" : "ready";
    }

    /**
     * 不能用的<b>具体原因</b> + 该改哪个键。
     *
     * <p>{@link #status()} 的 hint 与 {@link #chat} 的回答共用这一份 —— 只有一个地方拼文案，
     * 就不会出现"接口说 A、聊天回答说 B"的情况。
     */
    private String unavailableHint() {
        return props.isEnabled() ? NOT_CONFIGURED_TEXT : DISABLED_TEXT;
    }

    private void appendHistory(List<Map<String, Object>> messages, List<Map<String, String>> history) {
        if (history == null) return;
        // 只取最近 8 条：上下文越长越贵，而"多轮里更早的内容"对查数据帮助有限
        int from = Math.max(0, history.size() - 8);
        for (int i = from; i < history.size(); i++) {
            Map<String, String> item = history.get(i);
            if (item == null) continue;
            String role = item.get("role");
            String content = item.get("content");
            if (content == null || content.trim().isEmpty()) continue;
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            messages.add(msg(role, content));
        }
    }

    private Map<String, Object> answer(String text, List<String> toolsUsed, boolean configured) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("answer", text);
        map.put("tools", toolsUsed);
        map.put("configured", configured);
        return map;
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }

    private Map<String, Object> toolMsg(String callId, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "tool");
        map.put("tool_call_id", callId);
        map.put("content", content == null ? "" : content);
        return map;
    }

    private Map<String, Object> readJson(String raw) {
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("解析 AI 服务响应失败：" + truncate(raw));
        }
    }

    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            String value = str(map.get(key));
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String join(String base, String path) {
        String left = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String right = path.startsWith("/") ? path : "/" + path;
        return left + right;
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
