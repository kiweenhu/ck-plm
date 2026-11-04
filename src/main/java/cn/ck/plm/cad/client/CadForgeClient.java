/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.client;

import cn.ck.plm.cad.config.CadForgeProperties;
import cn.ck.plm.cad.dto.ForgeJobRequest;
import cn.ck.plm.cad.dto.ForgeJobResponse;
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
 * 转换器（CK-CADForge）的 HTTP 客户端 —— 提交、查状态、取消、探活。
 *
 * <h3>出站安全</h3>
 * 每次调用前过一遍 {@link OutboundUrlGuard}。转换器一般部署在内网，
 * 内网地址默认是被拦的（防 SSRF），所以必须显式配白名单 —— 这是有意为之：
 * "能调通"不该来自"恰好没拦住"。
 *
 * <h3>为什么不用连接池那套</h3>
 * 调用频率很低（用户上传 CAD 时才有），SimpleClientHttpRequestFactory 足够，
 * 不值得为此引入额外依赖。
 */
@Component
public class CadForgeClient {

    private static final Logger log = LoggerFactory.getLogger(CadForgeClient.class);

    private final CadForgeProperties properties;
    private final IntegrationProperties integrationProperties;
    private volatile RestClient restClient;

    public CadForgeClient(CadForgeProperties properties,
                          IntegrationProperties integrationProperties) {
        this.properties = properties;
        this.integrationProperties = integrationProperties;
    }

    /**
     * 提交转换任务。
     *
     * @return 受理结果；转换器不可用时返回 null（由调用方决定降级策略）
     */
    public ForgeJobResponse submitJob(ForgeJobRequest request) {
        String url = properties.getBaseUrl() + "/api/v1/jobs";
        OutboundUrlGuard.assertAllowed(url, integrationProperties);
        try {
            return client().post()
                    .uri("/api/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ForgeJobResponse.class);
        } catch (Exception e) {
            log.warn("提交转换任务失败: url={}, externalId={}, err={}", url, request.getExternalId(), e.getMessage());
            return null;
        }
    }

    /**
     * 主动查询任务状态 —— 回调丢失时的补偿通道。
     *
     * @return 状态信息（Map 形式，避免与回调结构强耦合）；查询失败返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryJob(String jobId) {
        OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
        try {
            return client().get()
                    .uri("/api/v1/jobs/{jobId}", jobId)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("查询转换任务失败: jobId={}, err={}", jobId, e.getMessage());
            return null;
        }
    }

    /**
     * 取消在途任务 —— <b>尽力而为</b>。
     *
     * <p>转换器侧只能杀掉子进程（排队中的任务直接丢弃）。返回 false 不代表取消失败：
     * 任务可能已经跑完、或转换器正好重启过。调用方应以 <b>本地状态</b> 为准，
     * 别把"转换器说没这个任务"当成"用户没取消成功"。
     *
     * @return 转换器确认收到取消请求
     */
    public boolean cancelJob(String jobId) {
        OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
        try {
            client().delete()
                    .uri("/api/v1/jobs/{jobId}", jobId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("取消转换任务失败（以本地状态为准）: jobId={}, err={}", jobId, e.getMessage());
            return false;
        }
    }

    /**
     * 下载产物到临时文件 —— 共享文件卷不可用时的兜底通道。
     *
     * <h3>为什么返回 {@code Path} 而不是 {@code byte[]}</h3>
     * 同机部署时这条路径几乎不走（产物就在共享卷上，直接读）；
     * 一旦分布式部署、没挂共享卷，<b>每次产物回传都要走它</b> ——
     * GLB 动辄几十到几百 MB，全量读进堆等于拿堆内存换一次文件拷贝，
     * 而且并发转换时是<b>每个任务一份</b>。这里改成流式落盘：
     * 内存占用与产物大小无关。
     *
     * <h3>调用方的责任</h3>
     * 返回的临时文件<b>由调用方在用完后删除</b>（本类不知道它什么时候读完）。
     * 下载中途失败时这里自己清理，不留半截文件。
     */
    public Path downloadArtifactToTemp(String jobId, String artifactName) {
        OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
        try {
            return downloadClient().get()
                    .uri("/api/v1/jobs/{jobId}/artifacts/{name}", jobId, artifactName)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() != 200) {
                            log.warn("下载转换产物失败: jobId={}, artifact={}, http={}",
                                    jobId, artifactName, response.getStatusCode().value());
                            return null;
                        }
                        Path tmp = Files.createTempFile("ckforge-artifact-", ".part");
                        try (InputStream in = response.getBody();
                             OutputStream out = Files.newOutputStream(tmp)) {
                            in.transferTo(out);
                        } catch (IOException e) {
                            // 半截文件会留在这儿没人管，下一次 GC 也清不掉（不在堆里）
                            Files.deleteIfExists(tmp);
                            throw e;
                        }
                        return tmp;
                    });
        } catch (Exception e) {
            log.warn("下载转换产物失败: jobId={}, artifact={}, err={}", jobId, artifactName, e.getMessage());
            return null;
        }
    }

    /**
     * 连通性探测 —— 页面上的「测试连接」。
     *
     * <p>不抛异常：连不上是<b>预期结果</b>之一，页面需要看到原因而不是一个 500。
     * 返回体里带转换器自报的引擎（{@code occt} / {@code mock}）与车道配置，
     * 顺手就能发现"起了个没装 OCCT 的解释器"这类部署事故。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            OutboundUrlGuard.assertAllowed(properties.getBaseUrl(), integrationProperties);
            Map<String, Object> body = client().get()
                    .uri("/healthz")
                    .retrieve()
                    .body(Map.class);
            result.put("ok", body != null && Boolean.TRUE.equals(body.get("ok")));
            if (body != null) {
                result.put("engine", body.get("engine"));
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

    /**
     * 配置变更后清掉缓存的客户端。
     *
     * <p>{@code RestClient} 里固化着 baseUrl 与超时，不重建的话，页面上改了地址
     * 实际还在往老地址发（而且报错信息会指向一个"明明已经改掉"的地方，最难查）。
     */
    public void invalidate() {
        synchronized (this) {
            this.restClient = null;
        }
    }

    /**
     * 产物下载专用客户端 —— 只有读超时与 {@link #client()} 不同。
     *
     * <p>不能复用提交任务那个客户端：它固化着 10 秒读超时（那是"提交该在毫秒级返回"
     * 的合理预期），而几百 MB 的下载在慢网络上两次 read 之间就可能超过 10 秒，
     * 结果是被掐断在半路且不报超时。下载频率很低，每次现建一个客户端的开销可以忽略。
     */
    private RestClient downloadClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(Math.max(properties.getReadTimeoutMs(), properties.getArtifactReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** 懒构建：配置可能在运行时才确定，且超时参数要跟着配置走 */
    private RestClient client() {
        RestClient local = restClient;
        if (local == null) {
            synchronized (this) {
                if (restClient == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(properties.getConnectTimeoutMs());
                    factory.setReadTimeout(properties.getReadTimeoutMs());
                    restClient = RestClient.builder()
                            .baseUrl(properties.getBaseUrl())
                            .requestFactory(factory)
                            .build();
                }
                local = restClient;
            }
        }
        return local;
    }
}
