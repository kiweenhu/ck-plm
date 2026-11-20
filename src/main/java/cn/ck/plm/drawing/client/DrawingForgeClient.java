/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.client;

import cn.ck.plm.drawing.config.DrawingForgeProperties;
import cn.ck.plm.drawing.dto.DrawingForgeJobRequest;
import cn.ck.plm.drawing.dto.DrawingForgeJobResponse;
import cn.ck.plm.integration.config.IntegrationProperties;
import cn.ck.plm.integration.util.OutboundUrlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * CK-DrawingForge 的 HTTP 客户端 —— 提交、查状态、取消、探活、下载产物。
 * 与 CadForgeClient 同构；每次调用前过 OutboundUrlGuard（转换器在内网时须显式白名单）。
 */
@Component
public class DrawingForgeClient {

    private static final Logger log = LoggerFactory.getLogger(DrawingForgeClient.class);

    private final DrawingForgeProperties properties;
    private final IntegrationProperties integrationProperties;
    private volatile RestClient restClient;

    public DrawingForgeClient(DrawingForgeProperties properties,
                              IntegrationProperties integrationProperties) {
        this.properties = properties;
        this.integrationProperties = integrationProperties;
    }

    /** 提交转换任务；转换器不可用时返回 null（由调用方决定降级）。 */
    public DrawingForgeJobResponse submitJob(DrawingForgeJobRequest request) {
        String url = properties.getBaseUrl() + "/api/v1/jobs";
        OutboundUrlGuard.assertAllowed(url, integrationProperties);
        try {
            return client().post()
                    .uri("/api/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DrawingForgeJobResponse.class);
        } catch (Exception e) {
            log.warn("提交二维转换任务失败: url={}, externalId={}, err={}",
                    url, request.getExternalId(), e.getMessage());
            return null;
        }
    }

    /** 主动查询任务状态 —— 回调丢失时的补偿通道。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryJob(String jobId) {
        OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
        try {
            return client().get()
                    .uri("/api/v1/jobs/{jobId}", jobId)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("查询二维转换任务失败: jobId={}, err={}", jobId, e.getMessage());
            return null;
        }
    }

    /** 取消在途任务（尽力而为，以本地状态为准）。 */
    public boolean cancelJob(String jobId) {
        OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
        try {
            client().delete().uri("/api/v1/jobs/{jobId}", jobId).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("取消二维转换任务失败（以本地状态为准）: jobId={}, err={}", jobId, e.getMessage());
            return false;
        }
    }

    /** 下载产物到临时文件（共享卷不可用时的兜底），调用方用完须删除。 */
    public Path downloadArtifactToTemp(String jobId, String artifactName) {
        OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
        try {
            return downloadClient().get()
                    .uri("/api/v1/jobs/{jobId}/artifacts/{name}", jobId, artifactName)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() != 200) {
                            log.warn("下载二维转换产物失败: jobId={}, artifact={}, http={}",
                                    jobId, artifactName, response.getStatusCode().value());
                            return null;
                        }
                        Path tmp = Files.createTempFile("ckdrawing-artifact-", ".part");
                        try (InputStream in = response.getBody();
                             OutputStream out = Files.newOutputStream(tmp)) {
                            in.transferTo(out);
                        } catch (IOException e) {
                            Files.deleteIfExists(tmp);
                            throw e;
                        }
                        return tmp;
                    });
        } catch (Exception e) {
            log.warn("下载二维转换产物失败: jobId={}, artifact={}, err={}",
                    jobId, artifactName, e.getMessage());
            return null;
        }
    }

    /** 连通性探测 —— 页面「测试连接」。不抛异常：连不上是预期结果之一。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
            Map<String, Object> body = client().get().uri("/healthz").retrieve().body(Map.class);
            result.put("ok", body != null && Boolean.TRUE.equals(body.get("ok")));
            if (body != null) {
                result.put("engine", body.get("engine"));
                result.put("engineVersion", body.get("engineVersion"));
                result.put("maxConcurrency", body.get("maxConcurrency"));
                result.put("lanes", body.get("lanes"));
                result.put("queued", body.get("queued"));
                result.put("running", body.get("running"));
            }
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        result.put("latencyMs", System.currentTimeMillis() - startedAt);
        return result;
    }

    /** 配置变更后清掉缓存的客户端（RestClient 固化了 baseUrl 与超时）。 */
    public void invalidate() {
        synchronized (this) {
            this.restClient = null;
        }
    }

    private RestClient downloadClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(Math.max(properties.getReadTimeoutMs(),
                (int) Math.min(Integer.MAX_VALUE, properties.getArtifactReadTimeoutMs())));
        return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    private RestClient client() {
        RestClient local = restClient;
        if (local == null) {
            synchronized (this) {
                if (restClient == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(properties.getConnectTimeoutMs());
                    factory.setReadTimeout(properties.getReadTimeoutMs());
                    restClient = RestClient.builder().baseUrl(properties.getBaseUrl())
                            .requestFactory(factory).build();
                }
                local = restClient;
            }
        }
        return local;
    }
}
