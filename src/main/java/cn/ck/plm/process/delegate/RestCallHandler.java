/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.integration.config.IntegrationProperties;
import cn.ck.plm.integration.entity.TargetSystem;
import cn.ck.plm.integration.service.TargetSystemService;
import cn.ck.plm.integration.util.OutboundUrlGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动服务 {@code integration.restCall} —— 「REST 接口调用」节点。
 *
 * <p>流程里"调一个外部接口 + 传几个参数（多数来自流程变量）"的场景，以前只能：
 * ① 用系统集成（报文结构固定，只能把业务对象集合发过去）；② 让后端写一个 handler（每接一个接口发一次版）。
 * 本服务补的就是中间那条：<b>系统与凭据来自目标系统注册表，路径/方法/参数由节点配置</b>。
 *
 * <h3>参数约定（节点上填）</h3>
 * <ul>
 *   <li>{@code systemCode} 目标系统编码（注册表里的 code，必填）；</li>
 *   <li>{@code path} 接口路径（拼在系统地址之后，必填）；</li>
 *   <li>{@code method} GET / POST / PUT / PATCH（不填按 POST）；</li>
 *   <li><b>其余参数一律当作"要传过去的业务参数"</b>：GET 拼成查询串，其余方法作为 JSON 报文。</li>
 * </ul>
 *
 * <p>参数取值里的 {@code ${流程变量}} 在 {@link PlmServiceDelegate} 里已经替换完毕
 * （那是所有服务共用的读参数入口），本类拿到的已经是最终值。
 *
 * <h3>失败即中断</h3>
 * <p>超时 / 连不上 / 非 2xx 一律抛异常，让引擎回滚这一步并落节点日志 ——
 * 与系统集成节点一致：对方系统没收到数据、而流程显示"已过"，是最难查的一类问题。
 */
@Component
public class RestCallHandler implements PlmServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(RestCallHandler.class);

    /** 服务 id —— 必须与前端 {@code BUILTIN_SERVICES} 里的声明一致 */
    public static final String SERVICE_ID = "integration.restCall";

    /**
     * 控制参数名。刻意不用 {@code apiPath} / {@code httpMethod}：设计器面板的字段 key 是
     * {@code params.<参数名>}，而「系统集成」已经占了那两个名字，同名会在同一节点类型下重复。
     */
    private static final String PARAM_SYSTEM_CODE = "systemCode";
    private static final String PARAM_API_PATH = "path";
    private static final String PARAM_METHOD = "method";

    private static final String AUTH_NONE = "NONE";
    private static final String AUTH_BASIC = "BASIC";
    private static final String AUTH_BEARER = "BEARER";
    private static final String AUTH_API_KEY = "API_KEY";

    /** 出错信息里响应体的截断长度（全量报文进日志就够，给办理人看的不必太长） */
    private static final int SNIPPET_MAX = 300;

    private final TargetSystemService targetSystemService;
    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public RestCallHandler(TargetSystemService targetSystemService, IntegrationProperties properties,
                           ObjectMapper objectMapper) {
        this.targetSystemService = targetSystemService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void execute(PlmServiceContext context) {
        String systemCode = required(context, PARAM_SYSTEM_CODE, "目标系统");
        String apiPath = required(context, PARAM_API_PATH, "接口路径");
        String method = upperOrDefault(context.param(PARAM_METHOD), "POST");

        TargetSystem system = targetSystemService.findByCode(systemCode, resolveTenant(context));
        if (system == null) {
            throw new IllegalArgumentException("「REST 接口调用」找不到目标系统「" + systemCode
                    + "」（节点「" + context.nodeName() + "」）。请让管理员在「系统配置 → 目标系统」里登记它，"
                    + "或改选已登记的系统");
        }
        if (Boolean.FALSE.equals(system.getEnabled())) {
            throw new IllegalArgumentException("目标系统「" + system.getName() + "」（" + systemCode
                    + "）已停用，请管理员启用后再跑，或改选其他系统");
        }

        String url = joinUrl(system.getBaseUrl(), apiPath);
        Map<String, String> callParams = callParams(context);
        // 出站前先过安全策略：私网/回环/云元数据地址一律拦（白名单里的除外）
        OutboundUrlGuard.assertAllowed(url, properties);

        URI uri = URI.create("GET".equals(method) ? withQuery(url, callParams) : url);
        String body = "GET".equals(method) ? null : writeJson(callParams);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .header("Accept", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) {
            builder.header("Content-Type", "application/json; charset=UTF-8");
        }
        applyAuth(builder, system);

        HttpResponse<String> response;
        try {
            response = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("「REST 接口调用」被中断：" + method + " " + uri);
        } catch (Exception e) {
            log.error("REST 接口调用失败: method={}, url={}, error={}", method, uri, e.getMessage(), e);
            throw new IllegalArgumentException("「REST 接口调用」无法连接 " + uri + "（" + e.getMessage()
                    + "），请检查目标系统地址与网络");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = abbreviate(response.body());
            log.error("REST 接口调用被拒绝: method={}, url={}, status={}, body={}",
                    method, uri, response.statusCode(), snippet);
            throw new IllegalArgumentException("「REST 接口调用」被 " + uri + " 拒绝：HTTP "
                    + response.statusCode() + (snippet.isEmpty() ? "" : "（" + snippet + "）"));
        }
        log.info("REST 接口调用成功: method={}, url={}, system={}, params={}, status={}",
                method, uri, systemCode, callParams.keySet(), response.statusCode());
    }

    /**
     * 业务参数 = 节点参数里除三样控制项之外的全部。
     *
     * <p>这样设计的理由：流程设计者填的就是"要传什么"，没必要再让他把参数名重复写进数组 ——
     * 多余的中间层只会多一处可以填错的地方。
     */
    private Map<String, String> callParams(PlmServiceContext context) {
        Map<String, String> params = new LinkedHashMap<>();
        context.params().forEach((name, value) -> {
            if (PARAM_SYSTEM_CODE.equals(name) || PARAM_API_PATH.equals(name) || PARAM_METHOD.equals(name)) {
                return;
            }
            if (value != null && !value.isEmpty()) {
                params.put(name, value);
            }
        });
        return params;
    }

    /** 租户：优先取流程实例的租户，取不到再回落到线程上下文（异步作业线程里可能没有） */
    private String resolveTenant(PlmServiceContext context) {
        String tenantOid = context.tenantOid();
        if (tenantOid == null || tenantOid.isEmpty()) {
            tenantOid = TenantContext.get();
        }
        if (tenantOid == null || tenantOid.isEmpty()) {
            throw new IllegalArgumentException("「REST 接口调用」取不到流程实例所属租户，"
                    + "无法定位目标系统。请联系管理员检查流程实例的租户信息");
        }
        return tenantOid;
    }

    private void applyAuth(HttpRequest.Builder builder, TargetSystem system) {
        String authType = upperOrDefault(system.getAuthType(), AUTH_NONE);
        String username = trimToNull(system.getUsername());
        String secret = trimToNull(system.getSecret());
        switch (authType) {
            case AUTH_NONE:
                return;
            case AUTH_BASIC: {
                if (username == null || secret == null) {
                    throw new IllegalArgumentException("目标系统「" + system.getName()
                            + "」配置的是 Basic 认证，但缺少用户名或口令，请管理员补齐");
                }
                String raw = username + ":" + secret;
                builder.header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
                return;
            }
            case AUTH_BEARER: {
                if (secret == null) {
                    throw new IllegalArgumentException("目标系统「" + system.getName()
                            + "」配置的是 Bearer Token 认证，但缺少 Token，请管理员补齐");
                }
                builder.header("Authorization", "Bearer " + secret);
                return;
            }
            case AUTH_API_KEY: {
                if (username == null || secret == null) {
                    throw new IllegalArgumentException("目标系统「" + system.getName()
                            + "」配置的是 API Key 认证，但缺少请求头名称或取值，请管理员补齐");
                }
                // API Key 形态：头名称由管理员给（如 X-API-Key），取值是 secret
                builder.header(username, secret);
                return;
            }
            default:
                throw new IllegalArgumentException("目标系统「" + system.getName() + "」的认证方式「"
                        + authType + "」无法识别（支持 NONE / BASIC / BEARER / API_KEY），请管理员重新选择");
        }
    }

    private String writeJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("「REST 接口调用」的请求参数无法序列化：" + e.getMessage());
        }
    }

    /** GET：参数拼成查询串（值做 URL 编码，中文与特殊字符不能裸放）。静态是为了能直接单测地址拼装 */
    static String withQuery(String url, Map<String, String> params) {
        if (params.isEmpty()) {
            return url;
        }
        StringBuilder query = new StringBuilder();
        params.forEach((name, value) -> {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    static String joinUrl(String baseUrl, String apiPath) {
        String left = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String right = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        return left + right;
    }

    private String required(PlmServiceContext context, String name, String label) {
        String value = context.param(name);
        if (value == null) {
            throw new IllegalArgumentException("「REST 接口调用」节点「" + context.nodeName()
                    + "」缺少" + label + "（" + name + "），请在设计器里补上");
        }
        return value;
    }

    private String upperOrDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String single = body.replaceAll("\\s+", " ").trim();
        return single.length() <= SNIPPET_MAX ? single : single.substring(0, SNIPPET_MAX) + "…";
    }
}
