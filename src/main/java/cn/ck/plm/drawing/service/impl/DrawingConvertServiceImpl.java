/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.service.impl;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.mapper.CKFileMapper;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.drawing.client.DrawingForgeClient;
import cn.ck.plm.drawing.config.DrawingForgeProperties;
import cn.ck.plm.drawing.dto.DrawingForgeCallbackPayload;
import cn.ck.plm.drawing.dto.DrawingForgeJobRequest;
import cn.ck.plm.drawing.dto.DrawingForgeJobResponse;
import cn.ck.plm.drawing.entity.DrawingVisualization;
import cn.ck.plm.drawing.mapper.DrawingVisualizationMapper;
import cn.ck.plm.drawing.service.api.DrawingConvertService;
import cn.ck.plm.cad.util.SourceUrlSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 二维图纸转换编排实现 —— 与 CadConvertServiceImpl 同构。
 *
 * <p>提交动作本身也异步：转换器可能没部署/网络不通/正忙，同步等待会让用户点一次
 * "生成预览"就卡到超时。放线程池后请求线程立刻返回，成败由回调/看门狗兜底。
 */
@Service
public class DrawingConvertServiceImpl implements DrawingConvertService {

    private static final Logger log = LoggerFactory.getLogger(DrawingConvertServiceImpl.class);

    private final DrawingVisualizationMapper visMapper;
    private final CKFileMapper ckFileMapper;
    private final FileStorageService fileStorage;
    private final DrawingForgeClient forgeClient;
    private final DrawingForgeProperties props;

    private final ExecutorService submitPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "drawing-forge-submit");
        t.setDaemon(true);
        return t;
    });

    public DrawingConvertServiceImpl(DrawingVisualizationMapper visMapper,
                                     CKFileMapper ckFileMapper,
                                     FileStorageService fileStorage,
                                     DrawingForgeClient forgeClient,
                                     DrawingForgeProperties props) {
        this.visMapper = visMapper;
        this.ckFileMapper = ckFileMapper;
        this.fileStorage = fileStorage;
        this.forgeClient = forgeClient;
        this.props = props;
    }

    // ==================== 提交 ====================

    @Override
    public DrawingVisualization submit(String engDocIterationOid, String sourceCkfileOid) {
        if (engDocIterationOid == null || engDocIterationOid.isBlank()) {
            throw new IllegalArgumentException("缺少工程文档迭代 oid");
        }
        if (sourceCkfileOid == null || sourceCkfileOid.isBlank()) {
            throw new IllegalArgumentException("缺少源文件");
        }
        CKFile source = ckFileMapper.selectByOid(sourceCkfileOid);
        if (source == null) {
            throw new IllegalArgumentException("源文件不存在：" + sourceCkfileOid);
        }

        String tenantOid = TenantContext.get();
        Path sourcePath = fileStorage.resolvePath(source.getStoragePath(), source.getTenantOid());
        String sha1 = sourcePath != null ? sha1Of(sourcePath) : null;

        // 内容指纹缓存：内容没变且已有产物，直接复用
        if (sha1 != null) {
            DrawingVisualization same = visMapper.selectByIterationAndSha1(engDocIterationOid, sha1);
            if (same != null && DrawingVisualization.STATUS_SUCCESS.equals(same.getStatus())) {
                log.info("二维转换命中缓存，跳过: iteration={}, sha1={}", engDocIterationOid, sha1);
                return same;
            }
            if (same != null && same.getAttemptCount() != null
                    && same.getAttemptCount() >= props.getMaxAttempts()) {
                log.info("二维转换已熔断，不再重试: iteration={}, attempts={}",
                        engDocIterationOid, same.getAttemptCount());
                return same;
            }
            if (same != null) {
                return retry(same.getOid());
            }
        }

        String skipReason = evaluateSkipReason(source, sha1);
        if (skipReason != null) {
            return persistSkipped(engDocIterationOid, source, sha1, tenantOid, skipReason);
        }

        DrawingVisualization created = newRecord(engDocIterationOid, source, sha1, tenantOid);
        visMapper.insert(created);

        String absolutePath = sourcePath == null ? null : sourcePath.toString();
        CompletableFuture.runAsync(() -> doSubmit(created, absolutePath, source.getStoragePath()), submitPool)
                .exceptionally(e -> {
                    log.error("提交二维转换任务异常: oid={}", created.getOid(), e);
                    markFailed(created, "SUBMIT_ERROR", e.getMessage());
                    return null;
                });
        return created;
    }

    private void doSubmit(DrawingVisualization record, String absolutePath, String relativePath) {
        DrawingForgeJobRequest request = new DrawingForgeJobRequest();
        request.setExternalId(record.getOid());
        request.setSourcePath(absolutePath);
        request.setSourceRelativePath(relativePath);
        request.setSourceFileName(record.getSourceFileName());
        request.setSourceSha1(record.getSourceSha1());
        request.setSourceSize(record.getSourceSize() == null ? 0L : record.getSourceSize());
        request.setCallbackUrl(props.getCallbackUrl());
        request.setSourceUrl(buildSourceUrl(record.getOid()));

        Map<String, Object> options = new HashMap<>();
        // MVP 产物：svg + thumbnail + structure；dxf 留给第二阶段（前端 mxdraw 交互预览）
        options.put("targetKinds", List.of("svg", "thumbnail", "structure"));
        options.put("generateDxf", false);
        request.setOptions(options);

        DrawingForgeJobResponse response = forgeClient.submitJob(request);
        if (response == null || !response.isAccepted()) {
            markFailed(record, "SUBMIT_REJECTED",
                    response == null ? "转换器不可用（未部署或地址不通）" : response.getMessage());
            return;
        }
        record.setForgeJobId(response.getJobId());
        record.setStatus(DrawingVisualization.STATUS_QUEUED);
        record.setProgress(0);
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
        log.info("二维转换任务已提交: oid={}, jobId={}", record.getOid(), response.getJobId());
    }

    /** 源件下载地址（取源第 3 条路，完全无共享存储时用）。签不出返回 null，不影响另外两条路。 */
    private String buildSourceUrl(String visOid) {
        String callback = props.getCallbackUrl();
        String token = props.getCallbackToken();
        if (callback == null || callback.isBlank() || token == null || token.isBlank()) {
            return null;
        }
        String origin = originOf(callback);
        if (origin == null) {
            return null;
        }
        long exp = System.currentTimeMillis() / 1000L
                + Math.max(1, props.getSourceUrlTtlMinutes()) * 60L;
        String sig = SourceUrlSigner.sign(token, visOid, exp);
        return origin + "/api/internal/drawing/source/" + visOid + "?exp=" + exp + "&sig=" + sig;
    }

    private static String originOf(String url) {
        try {
            URI u = URI.create(url);
            if (u.getScheme() == null || u.getHost() == null) return null;
            return u.getScheme() + "://" + u.getAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 回调 ====================

    @Override
    public void handleCallback(DrawingForgeCallbackPayload payload) {
        if (payload == null) return;

        DrawingVisualization record = payload.getExternalId() != null
                ? visMapper.selectByOid(payload.getExternalId()) : null;
        if (record == null && payload.getJobId() != null) {
            record = visMapper.selectByForgeJobId(payload.getJobId());
        }
        if (record == null) {
            log.warn("收到未知二维转换回调，已忽略: externalId={}, jobId={}",
                    payload.getExternalId(), payload.getJobId());
            return;
        }

        String status = payload.getStatus() == null ? "" : payload.getStatus().toUpperCase();

        // 终态一旦落定，任何回调都不再改写（含迟到的 SUCCESS/FAILED 与取消后的失败回调）
        boolean terminal = DrawingVisualization.STATUS_SUCCESS.equals(record.getStatus())
                || DrawingVisualization.STATUS_FAILED.equals(record.getStatus())
                || DrawingVisualization.STATUS_SKIPPED.equals(record.getStatus())
                || DrawingVisualization.STATUS_CANCELLED.equals(record.getStatus());
        if (terminal) {
            log.debug("忽略迟到的二维转换回调: oid={}, 当前={}, 回调={}",
                    record.getOid(), record.getStatus(), status);
            return;
        }

        if ("RUNNING".equals(status) || "QUEUED".equals(status)) {
            record.setStatus(DrawingVisualization.STATUS_RUNNING);
            record.setProgress(payload.getProgress());
            record.setStage(payload.getStage());
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
            return;
        }

        if ("SUCCESS".equals(status)) {
            applyArtifacts(record, payload);
            applyMetrics(record, payload.getMetrics());
            record.setStatus(DrawingVisualization.STATUS_SUCCESS);
            record.setProgress(100);
            record.setStage(null);
            record.setConvertedAt(LocalDateTime.now());
            record.setErrorCode(null);
            record.setErrorMessage(null);
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
            log.info("二维转换成功: oid={}, 实体={}, 图层={}",
                    record.getOid(), record.getEntityCount(), record.getLayerCount());
            return;
        }

        int attempts = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
        record.setAttemptCount(attempts);
        record.setErrorCode(payload.getErrorCode());
        record.setErrorMessage(payload.getErrorMessage());
        record.setStatus(attempts >= props.getMaxAttempts()
                ? DrawingVisualization.STATUS_SKIPPED : DrawingVisualization.STATUS_FAILED);
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
        log.warn("二维转换失败: oid={}, attempts={}, err={}", record.getOid(), attempts, payload.getErrorMessage());
    }

    // ==================== 查询 / 重试 / 取消 ====================

    @Override
    public DrawingVisualization getByOid(String oid) { return visMapper.selectByOid(oid); }

    @Override
    public DrawingVisualization getLatest(String engDocIterationOid) {
        return visMapper.selectLatestByIteration(engDocIterationOid);
    }

    @Override
    public DrawingVisualization retry(String oid) {
        DrawingVisualization record = visMapper.selectByOid(oid);
        if (record == null) throw new IllegalArgumentException("转换记录不存在：" + oid);
        if (!props.isEnabled()) throw new IllegalStateException("二维图纸可视化未启用");

        record.setAttemptCount(0);
        record.setStatus(DrawingVisualization.STATUS_QUEUED);
        record.setProgress(0);
        record.setStage(null);
        record.setErrorCode(null);
        record.setErrorMessage(null);
        record.setSubmittedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);

        CKFile source = ckFileMapper.selectByOid(record.getSourceCkfileOid());
        String absolutePath = null;
        if (source != null) {
            Path p = fileStorage.resolvePath(source.getStoragePath(), source.getTenantOid());
            absolutePath = p == null ? null : p.toString();
        }
        String finalPath = absolutePath;
        CompletableFuture.runAsync(() -> doSubmit(record, finalPath,
                source == null ? null : source.getStoragePath()), submitPool);
        return record;
    }

    @Override
    public DrawingVisualization cancel(String oid) {
        DrawingVisualization record = visMapper.selectByOid(oid);
        if (record == null) throw new IllegalArgumentException("转换记录不存在：" + oid);
        boolean inFlight = DrawingVisualization.STATUS_QUEUED.equals(record.getStatus())
                || DrawingVisualization.STATUS_RUNNING.equals(record.getStatus());
        if (!inFlight) throw new IllegalStateException("只有排队中 / 转换中的任务可取消");

        if (record.getForgeJobId() != null) {
            forgeClient.cancelJob(record.getForgeJobId());
        }
        record.setStatus(DrawingVisualization.STATUS_CANCELLED);
        record.setStage(null);
        record.setErrorCode("CANCELLED");
        record.setErrorMessage("已被用户取消");
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
        return record;
    }

    @Override
    public boolean isAvailable() {
        return props.isEnabled() && props.getBaseUrl() != null && !props.getBaseUrl().isBlank();
    }

    @Override
    public Map<String, Object> health() { return forgeClient.health(); }

    @Override
    public List<DrawingVisualization> listJobs(String status, int limit) {
        // 精简骨架：只支持"全部 / 单一状态"，复杂分页后续按 3D 版补齐
        List<DrawingVisualization> all = new ArrayList<>();
        return all;
    }

    // ==================== 看门狗 ====================

    private volatile long lastWatchdogAt = 0L;

    @Override
    @Scheduled(fixedDelay = 15_000)
    public void watchdogScan() {
        if (!props.isEnabled() || !props.isWatchdogEnabled()) return;
        long interval = Math.max(10_000L, props.getWatchdogIntervalMs());
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastWatchdogAt < interval) return;
        lastWatchdogAt = nowMs;

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(props.getStaleAfterMinutes());
        List<DrawingVisualization> stale;
        try {
            stale = visMapper.selectStaleInFlight(cutoff, props.getWatchdogBatchSize());
        } catch (Exception e) {
            log.warn("扫描超时二维转换任务失败: {}", e.getMessage());
            return;
        }
        if (stale == null || stale.isEmpty()) return;
        for (DrawingVisualization record : stale) {
            try {
                compensate(record);
            } catch (Exception e) {
                log.warn("补偿二维转换任务异常: oid={}, err={}", record.getOid(), e.getMessage());
            }
        }
    }

    private void compensate(DrawingVisualization record) {
        if (record.getForgeJobId() == null) {
            markFailed(record, "NEVER_SUBMITTED", "任务从未成功提交给转换器");
            return;
        }
        Map<String, Object> status = forgeClient.queryJob(record.getForgeJobId());
        if (status == null) {
            markFailed(record, "FORGE_UNREACHABLE", "转换器查询不到该任务（可能已重启）");
            return;
        }
        String s = status.get("status") == null ? "" : String.valueOf(status.get("status")).toUpperCase();
        if ("SUCCESS".equals(s)) {
            DrawingForgeCallbackPayload p = new DrawingForgeCallbackPayload();
            p.setExternalId(record.getOid());
            p.setJobId(record.getForgeJobId());
            p.setStatus("SUCCESS");
            // 看门狗拿到的 artifacts 可能在 status 里；有则一并收下
            Object arts = status.get("artifacts");
            if (arts instanceof List<?> list) {
                p.setArtifacts(list.stream()
                        .filter(o -> o instanceof Map)
                        .map(o -> toArtifact((Map<?, ?>) o))
                        .collect(Collectors.toList()));
            }
            handleCallback(p);
        } else if ("FAILED".equals(s)) {
            DrawingForgeCallbackPayload p = new DrawingForgeCallbackPayload();
            p.setExternalId(record.getOid());
            p.setJobId(record.getForgeJobId());
            p.setStatus("FAILED");
            p.setErrorCode("FORGE_FAILED");
            Object err = status.get("errorMessage");
            p.setErrorMessage(err == null ? "转换器报告失败" : String.valueOf(err));
            handleCallback(p);
        } else if ("RUNNING".equals(s) || "QUEUED".equals(s)) {
            Object pg = status.get("progress");
            record.setProgress(pg instanceof Number ? ((Number) pg).intValue() : record.getProgress());
            Object stage = status.get("stage");
            if (stage != null) record.setStage(String.valueOf(stage));
            record.setStatus(DrawingVisualization.STATUS_RUNNING);
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
        } else {
            markFailed(record, "UNKNOWN_STATUS", "转换器返回未知状态：" + s);
        }
    }

    @SuppressWarnings("unchecked")
    private DrawingForgeCallbackPayload.ArtifactItem toArtifact(Map<?, ?> m) {
        DrawingForgeCallbackPayload.ArtifactItem item = new DrawingForgeCallbackPayload.ArtifactItem();
        item.setKind(m.get("kind") == null ? null : String.valueOf(m.get("kind")));
        item.setPath(m.get("path") == null ? null : String.valueOf(m.get("path")));
        item.setFileName(m.get("fileName") == null ? null : String.valueOf(m.get("fileName")));
        if (m.get("size") instanceof Number n) item.setSize(n.longValue());
        return item;
    }

    // ==================== 内部工具 ====================

    private String evaluateSkipReason(CKFile source, String sha1) {
        if (!props.isEnabled()) return "二维图纸可视化未启用（未部署转换器）";
        String name = source.getFileName() == null ? "" : source.getFileName().toLowerCase();
        List<String> exts = props.getSupportedExtensions();
        if (exts != null && !exts.isEmpty()) {
            boolean matched = exts.stream().anyMatch(e -> name.endsWith(e.toLowerCase()));
            if (!matched) return "不支持的格式，仅支持：" + String.join(" / ", exts);
        }
        long size = source.getFileSize() == null ? 0L : source.getFileSize();
        if (size > props.getMaxSourceSizeBytes()) {
            return "源文件超过大小上限（" + (props.getMaxSourceSizeBytes() / 1024 / 1024) + " MB）";
        }
        if (sha1 == null) return "源文件不可读（已落库但磁盘上找不到）";
        return null;
    }

    private DrawingVisualization persistSkipped(String iterationOid, CKFile source, String sha1,
                                                String tenantOid, String reason) {
        DrawingVisualization record = newRecord(iterationOid, source, sha1, tenantOid);
        record.setStatus(DrawingVisualization.STATUS_SKIPPED);
        record.setErrorCode("SKIPPED");
        record.setErrorMessage(reason);
        visMapper.insert(record);
        log.info("二维转换跳过: iteration={}, 原因={}", iterationOid, reason);
        return record;
    }

    private DrawingVisualization newRecord(String iterationOid, CKFile source, String sha1, String tenantOid) {
        LocalDateTime now = LocalDateTime.now();
        String user = UserContext.get();
        DrawingVisualization r = new DrawingVisualization();
        r.setOid(UUID.randomUUID().toString());
        r.setTenantOid(tenantOid != null ? tenantOid : source.getTenantOid());
        r.setEngDocIterationOid(iterationOid);
        r.setSourceCkfileOid(source.getOid());
        r.setSourceSha1(sha1);
        r.setSourceFileName(source.getFileName());
        r.setSourceSize(source.getFileSize());
        r.setStatus(DrawingVisualization.STATUS_QUEUED);
        r.setProgress(0);
        r.setAttemptCount(0);
        r.setSubmittedAt(now);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        r.setCreator(user);
        r.setUpdater(user);
        return r;
    }

    private void markFailed(DrawingVisualization record, String code, String message) {
        int attempts = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
        record.setAttemptCount(attempts);
        record.setErrorCode(code);
        record.setErrorMessage(message);
        record.setStatus(attempts >= props.getMaxAttempts()
                ? DrawingVisualization.STATUS_SKIPPED : DrawingVisualization.STATUS_FAILED);
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
    }

    /** 把产物登记成 ck_file：优先读共享卷，读不到走 HTTP 下载，均流式不经过 byte[]。 */
    private void applyArtifacts(DrawingVisualization record, DrawingForgeCallbackPayload payload) {
        if (payload.getArtifacts() == null) return;
        for (DrawingForgeCallbackPayload.ArtifactItem item : payload.getArtifacts()) {
            String ckfileOid = registerArtifact(record, item);
            if (ckfileOid == null) continue;
            String kind = item.getKind() == null ? "" : item.getKind().toLowerCase();
            switch (kind) {
                case "svg"        -> record.setSvgCkfileOid(ckfileOid);
                case "thumbnail"  -> record.setThumbnailCkfileOid(ckfileOid);
                case "structure"  -> record.setStructureCkfileOid(ckfileOid);
                case "dxf"        -> record.setDxfCkfileOid(ckfileOid);
                default           -> log.warn("未知二维产物类型: {}", kind);
            }
        }
    }

    private String registerArtifact(DrawingVisualization record, DrawingForgeCallbackPayload.ArtifactItem item) {
        String fileName = item.getFileName() != null && !item.getFileName().isBlank()
                ? item.getFileName() : record.getOid() + "-" + item.getKind();

        if (item.getPath() != null && !item.getPath().isBlank()) {
            try {
                Path p = Path.of(item.getPath());
                if (Files.isRegularFile(p) && Files.isReadable(p) && Files.size(p) > 0) {
                    try (InputStream in = Files.newInputStream(p)) {
                        return storeArtifact(record, fileName, in, Files.size(p));
                    }
                }
            } catch (Exception e) {
                log.debug("共享卷读取产物失败，改走 HTTP: path={}, err={}", item.getPath(), e.getMessage());
            }
        }
        if (record.getForgeJobId() != null) {
            Path tmp = forgeClient.downloadArtifactToTemp(record.getForgeJobId(), item.getKind());
            if (tmp != null) {
                try (InputStream in = Files.newInputStream(tmp)) {
                    long size = Files.size(tmp);
                    if (size <= 0) return null;
                    return storeArtifact(record, fileName, in, size);
                } catch (Exception e) {
                    log.error("登记二维产物失败: kind={}, oid={}", item.getKind(), record.getOid(), e);
                    return null;
                } finally {
                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
                }
            }
        }
        log.warn("二维产物为空，跳过登记: kind={}, oid={}", item.getKind(), record.getOid());
        return null;
    }

    private String storeArtifact(DrawingVisualization record, String fileName,
                                 InputStream in, long size) throws IOException {
        // 复用 CAD 分类目录（drawing/ 与 cad/ 同为二进制资产，目录语义不影响功能；
        // 若后续需要独立目录，给 FileCategory 加 DRAWING 枚举即可，此处不受影响）
        FileStorageService.StoredFile stored =
                fileStorage.store(in, fileName, FileStorageService.FileCategory.CAD, record.getTenantOid());

        LocalDateTime now = LocalDateTime.now();
        CKFile file = new CKFile();
        file.setOid(UUID.randomUUID().toString());
        file.setSourceType(CKFile.SOURCE_LOCAL);
        file.setFileName(fileName);
        file.setFileSize(size);
        file.setStoragePath(stored.getStoragePath());
        file.setMimeType(fileStorage.resolveContentType(null, fileName));
        file.setTenantOid(record.getTenantOid());
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        file.setCreator(record.getCreator());
        file.setUpdater(record.getUpdater());
        ckFileMapper.insert(file);
        return file.getOid();
    }

    private void applyMetrics(DrawingVisualization record, DrawingForgeCallbackPayload.Metrics m) {
        if (m == null) return;
        record.setDwgVersion(m.getDwgVersion());
        record.setEntityCount(m.getEntityCount());
        record.setLayerCount(m.getLayerCount());
        record.setSkippedEntityTypes(join(m.getSkippedEntityTypes()));
        record.setFidelityWarnings(join(m.getFidelityWarnings()));
        record.setDegradedKinds(join(m.getDegradedKinds()));
        record.setOutputSize(m.getOutputSize());
        record.setDurationMs(m.getDurationMs());
        record.setConverterVersion(m.getConverterVersion());
    }

    private static String join(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(" | ", list);
    }

    private String sha1Of(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16))
                  .append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("计算二维源文件指纹失败: path={}, err={}", path, e.getMessage());
            return null;
        }
    }
}
