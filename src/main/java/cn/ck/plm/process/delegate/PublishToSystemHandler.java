/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动服务 {@code integration.publishToSystem} —— 「发布到目标系统」（系统集成节点）。
 *
 * <p>把本流程的<b>业务对象集合</b>（ProcessEntitySet：发起时选定的那批对象）按节点参数配置的
 * REST 接口发布到目标系统。参数全部来自设计器节点（见前端 {@code BUILTIN_SERVICES}）：
 * <ul>
 *   <li>{@code baseUrl} 目标系统地址（不含路径）；</li>
 *   <li>{@code apiPath} 发布接口路径（最终地址 = baseUrl + apiPath）；</li>
 *   <li>{@code httpMethod} POST / PUT / PATCH（默认 POST）；</li>
 *   <li>{@code authType} NONE / BASIC / BEARER / API_KEY；</li>
 *   <li>{@code username} Basic 用户名，或 API Key 认证时的<b>请求头名称</b>；</li>
 *   <li>{@code secret} Basic 口令 / Bearer Token / API Key 取值。</li>
 * </ul>
 *
 * <p><b>为什么是"这批对象"</b>：与「设置状态」同一口径 —— 一次流程可以带一批对象走审批，
 * 只发主对象会让其余几条悄悄没发出去，而流程看起来"都过了"，最难发现。
 *
 * <p><b>失败即失败</b>：非 2xx 一律抛异常（由 {@link PlmServiceDelegate} 记进
 * {@code ck_process_node_log} 并把流程停在当前节点）。<b>不重试、也不吞掉错误</b>：
 * 集成失败属于"人得知道并处理"的情况，静默继续会让对方系统缺数据而无人察觉。
 *
 * <p>凭据当前随流程定义保存（明文）。要做「目标系统注册表 + 加密存储」时，
 * 只需把 {@code systemCode} 换成引用、本类改从注册表读取，节点结构与编译产物都不受影响。
 */
@Component
public class PublishToSystemHandler implements PlmServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(PublishToSystemHandler.class);

    private static final String PARAM_BASE_URL = "baseUrl";
    private static final String PARAM_API_PATH = "apiPath";
    private static final String PARAM_METHOD = "httpMethod";
    private static final String PARAM_AUTH_TYPE = "authType";
    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_SECRET = "secret";

    private static final String AUTH_NONE = "NONE";
    private static final String AUTH_BASIC = "BASIC";
    private static final String AUTH_BEARER = "BEARER";
    private static final String AUTH_API_KEY = "API_KEY";

    /** 出站超时：发布是流程中的一步，不能把办理人卡住 —— 10 秒足够，超时即失败并留日志 */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;

    public PublishToSystemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serviceId() {
        return "integration.publishToSystem";
    }

    @Override
    public void execute(PlmServiceContext context) {
        String baseUrl = trimToNull(context.param(PARAM_BASE_URL));
        String apiPath = trimToNull(context.param(PARAM_API_PATH));
        if (baseUrl == null) {
            throw new IllegalArgumentException("「发布到目标系统」缺少目标系统地址（baseUrl），"
                    + "请让流程设计者补上");
        }
        if (apiPath == null) {
            throw new IllegalArgumentException("「发布到目标系统」缺少发布接口路径（apiPath），"
                    + "请让流程设计者补上");
        }
        List<PlmServiceTarget> targets = context.targets();
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("「发布到目标系统」没有可发布的业务对象"
                    + "（本流程实例没有关联对象集合），请检查发起时是否带上了业务对象");
        }

        String method = upperOrDefault(context.param(PARAM_METHOD), "POST");
        if ("GET".equals(method)) {
            // 发布的内容全在请求报文里，GET 带不了 —— 下拉里加了 GET（REST 接口调用要查数据用）
            // 之后，这里拦住并说清原因，比让 HttpClient 抛"方法不合法"好查得多
            throw new IllegalArgumentException("「发布到目标系统」不支持 GET（发布内容在请求报文中），"
                    + "请改用 POST / PUT / PATCH");
        }
        String authType = upperOrDefault(context.param(PARAM_AUTH_TYPE), AUTH_NONE);
        String url = joinUrl(baseUrl, apiPath);
        String body = buildPayload(context, targets);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        applyAuth(builder, authType, context);

        HttpResponse<String> response;
        try {
            response = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("「发布到目标系统」调用被中断：" + url);
        } catch (Exception e) {
            log.error("发布到目标系统失败: url={}, targets={}, error={}",
                    url, targets.size(), e.getMessage(), e);
            throw new IllegalArgumentException("「发布到目标系统」无法连接 " + url + "（"
                    + e.getMessage() + "），请检查目标系统地址与网络");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = abbreviate(response.body());
            log.error("发布到目标系统被拒绝: url={}, status={}, body={}",
                    url, response.statusCode(), snippet);
            throw new IllegalArgumentException("「发布到目标系统」被 " + url + " 拒绝：HTTP "
                    + response.statusCode() + (snippet.isEmpty() ? "" : "（" + snippet + "）"));
        }

        log.info("发布到目标系统成功: url={}, method={}, targets={}, status={}",
                url, method, targets.size(), response.statusCode());
    }

    /**
     * 请求体：流程标识 + 这批业务对象。
     *
     * <p>字段名用平台自身的语义（typeCode / entityOid / entityVersion / businessKey），
     * 对方系统按这份约定对接；需要更多字段（如对象名称、属性）时应扩这里，
     * 而不是在目标系统里再查一次 PLM。
     */
    private String buildPayload(PlmServiceContext context, List<PlmServiceTarget> targets) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("processInstanceId", context.processInstanceId());
        root.put("nodeName", context.nodeName());
        root.put("initiator", context.actor());
        root.put("publishedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        ArrayNode entities = root.putArray("entities");
        for (PlmServiceTarget target : targets) {
            ObjectNode item = entities.addObject();
            item.put("typeCode", target.getTypeCode());
            item.put("rootTypeCode", target.getRootTypeCode());
            item.put("entityOid", target.getEntityOid());
            item.put("entityVersion", target.getEntityVersion());
            item.put("businessKey", target.getBusinessKey());
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // 组装失败不该发生在运行期（结构固定），真发生就是代码问题，直接抛出留日志
            throw new IllegalStateException("组装发布报文失败: " + e.getMessage(), e);
        }
    }

    /** 按认证方式补请求头；缺凭据时给出"让设计者补"的明确说法，而不是发一个必然 401 的请求 */
    private void applyAuth(HttpRequest.Builder builder, String authType, PlmServiceContext context) {
        String username = trimToNull(context.param(PARAM_USERNAME));
        String secret = trimToNull(context.param(PARAM_SECRET));
        switch (authType) {
            case AUTH_NONE:
                return;
            case AUTH_BASIC: {
                if (username == null || secret == null) {
                    throw new IllegalArgumentException(
                            "「发布到目标系统」配置的是 Basic 认证，但缺少用户名或口令，请让流程设计者补上");
                }
                String raw = username + ":" + secret;
                builder.header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
                return;
            }
            case AUTH_BEARER: {
                if (secret == null) {
                    throw new IllegalArgumentException(
                            "「发布到目标系统」配置的是 Bearer Token 认证，但缺少 Token，请让流程设计者补上");
                }
                builder.header("Authorization", "Bearer " + secret);
                return;
            }
            case AUTH_API_KEY: {
                if (username == null || secret == null) {
                    throw new IllegalArgumentException(
                            "「发布到目标系统」配置的是 API Key 认证，但缺少请求头名称或取值，请让流程设计者补上");
                }
                // API Key 形态：头名称由设计者给（如 X-API-Key），取值是 secret
                builder.header(username, secret);
                return;
            }
            default:
                throw new IllegalArgumentException("「发布到目标系统」的认证方式「" + authType
                        + "」无法识别（支持 NONE / BASIC / BEARER / API_KEY），请让流程设计者重新选择");
        }
    }

    private String joinUrl(String baseUrl, String apiPath) {
        String left = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String right = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        return left + right;
    }

    private String upperOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 对方返回体只留一小段进错误消息：完整报文进日志，界面上一句话够用 */
    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
    }
}
