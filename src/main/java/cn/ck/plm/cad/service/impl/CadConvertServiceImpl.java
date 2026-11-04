/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.service.impl;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.mapper.CKFileMapper;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cad.client.CadForgeClient;
import cn.ck.plm.cad.config.CadForgeProperties;
import cn.ck.plm.cad.dto.CadConvertBatchItem;
import cn.ck.plm.cad.dto.CadConvertBatchResultVO;
import cn.ck.plm.cad.dto.CadConvertJobPageVO;
import cn.ck.plm.cad.dto.CadConvertStatsVO;
import cn.ck.plm.cad.dto.ContainerCadModelVO;
import cn.ck.plm.cad.dto.ForgeCallbackPayload;
import cn.ck.plm.cad.dto.ForgeJobRequest;
import cn.ck.plm.cad.dto.ForgeJobResponse;
import cn.ck.plm.cad.entity.CadVisualization;
import cn.ck.plm.cad.mapper.CadVisualizationMapper;
import cn.ck.plm.cad.service.api.CadConvertService;
import cn.ck.plm.cad.util.SourceUrlSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CAD 转换编排实现。
 *
 * <h3>为什么提交动作本身也要异步</h3>
 * 提交是一次 HTTP 调用，看似很快，但转换器可能没部署、网络不通、或者正忙。
 * 若同步等待，用户点一次"生成预览"要卡到超时；放到独立线程池里，
 * 请求线程立刻返回 QUEUED，成败由回调/看门狗兜底 —— 这才是"异步提交"的完整含义。
 *
 * <h3>没部署转换器时怎么办</h3>
 * {@link #isAvailable()} 返回 false，触发转换直接落 SKIPPED 并记原因，
 * 前端据此隐藏 3D 入口。绝不给用户一个永远转不完的加载圈 —— 那比"没有这个功能"更糟。
 */
@Service
public class CadConvertServiceImpl implements CadConvertService {

    private static final Logger log = LoggerFactory.getLogger(CadConvertServiceImpl.class);

    private final CadVisualizationMapper visMapper;
    private final CKFileMapper ckFileMapper;
    private final FileStorageService fileStorage;
    private final CadForgeClient forgeClient;
    private final CadForgeProperties props;

    /**
     * 提交专用线程池。
     *
     * <p>它只负责"把任务 POST 给转换器"这一下，不参与转换本身，所以线程数跟转换并发无关。
     * 4 个线程是为了批量场景：一次勾 200 份时，若转换器不可达，每条要等 connect timeout，
     * 2 个线程要排上几分钟才把所有记录标成 FAILED。
     */
    private final ExecutorService submitPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "cad-forge-submit");
        t.setDaemon(true);
        return t;
    });

    public CadConvertServiceImpl(CadVisualizationMapper visMapper,
                                 CKFileMapper ckFileMapper,
                                 FileStorageService fileStorage,
                                 CadForgeClient forgeClient,
                                 CadForgeProperties props) {
        this.visMapper = visMapper;
        this.ckFileMapper = ckFileMapper;
        this.fileStorage = fileStorage;
        this.forgeClient = forgeClient;
        this.props = props;
    }

    // ==================== 提交 ====================

    @Override
    public CadVisualization submit(String engDocIterationOid, String sourceCkfileOid) {
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
        CadVisualization record = visMapper.selectLatestByIteration(engDocIterationOid);

        // ---- 内容指纹缓存：内容没变且已有产物，直接复用 ----
        Path sourcePath = fileStorage.resolvePath(source.getStoragePath(), source.getTenantOid());
        String sha1 = sourcePath != null ? sha1Of(sourcePath) : null;
        CadVisualization sameSha1 = null;
        if (sha1 != null) {
            sameSha1 = visMapper.selectByIterationAndSha1(engDocIterationOid, sha1);
            if (sameSha1 != null && CadVisualization.STATUS_SUCCESS.equals(sameSha1.getStatus())) {
                log.info("CAD 转换命中缓存，跳过: iteration={}, sha1={}", engDocIterationOid, sha1);
                return sameSha1;
            }
            // 熔断：同一份畸形文件反复失败后不再尝试，避免每次点开都烧一遍 CPU
            if (sameSha1 != null && sameSha1.getAttemptCount() != null
                    && sameSha1.getAttemptCount() >= props.getMaxAttempts()) {
                log.info("CAD 转换已熔断，不再重试: iteration={}, attempts={}",
                        engDocIterationOid, sameSha1.getAttemptCount());
                return sameSha1;
            }
        }

        // ---- 前置校验：注定失败的不进流水线 ----
        String skipReason = evaluateSkipReason(source, sha1);

        // 同一份内容已有记录时只能复用那条（表上有唯一约束 uk_cadvis_iter_sha1）：
        // 直接 insert 会撞唯一键，用户连点两次「生成预览」就收到 500。
        if (sameSha1 != null) {
            if (skipReason != null) {
                sameSha1.setStatus(CadVisualization.STATUS_SKIPPED);
                sameSha1.setErrorCode("SKIPPED");
                sameSha1.setErrorMessage(skipReason);
                sameSha1.setUpdatedAt(LocalDateTime.now());
                visMapper.update(sameSha1);
                log.info("CAD 转换跳过（复用既有记录）: iteration={}, 原因={}", engDocIterationOid, skipReason);
                return sameSha1;
            }
            log.info("CAD 转换复用既有记录重提: oid={}, status={}", sameSha1.getOid(), sameSha1.getStatus());
            return retry(sameSha1.getOid());
        }

        if (skipReason != null) {
            return persistSkipped(engDocIterationOid, source, sha1, tenantOid, skipReason);
        }

        // ---- 建记录并异步提交 ----
        CadVisualization created = newRecord(engDocIterationOid, source, sha1, tenantOid);
        if (record != null && sha1 == null) {
            // 没有指纹（读不到源文件）时无法去重，仅记录，不阻断
            log.warn("源文件不可读，无法计算内容指纹: {}", sourceCkfileOid);
        }
        visMapper.insert(created);

        String absolutePath = sourcePath == null ? null : sourcePath.toString();
        CompletableFuture.runAsync(() -> doSubmit(created, absolutePath, source.getStoragePath()), submitPool)
                .exceptionally(e -> {
                    log.error("提交转换任务异常: oid={}", created.getOid(), e);
                    markFailed(created, "SUBMIT_ERROR", e.getMessage());
                    return null;
                });

        return created;
    }

    /** 真正发起提交（在线程池中执行） */
    private void doSubmit(CadVisualization record, String absolutePath, String relativePath) {
        ForgeJobRequest request = new ForgeJobRequest();
        request.setExternalId(record.getOid());
        request.setSourcePath(absolutePath);
        request.setSourceRelativePath(relativePath);
        request.setSourceFileName(record.getSourceFileName());
        request.setSourceSha1(record.getSourceSha1());
        request.setSourceSize(record.getSourceSize() == null ? 0L : record.getSourceSize());
        request.setCallbackUrl(props.getCallbackUrl());
        // 三个定位字段都发，由转换器按自己的环境挑一条能用的（详见 ForgeJobRequest）。
        // 为 null 也没关系 —— 转换器会退回到 sourcePath / sourceRelativePath。
        request.setSourceUrl(buildSourceUrl(record.getOid()));

        Map<String, Object> options = new HashMap<>();
        options.put("targetFormat", "glb");
        options.put("generateStructure", true);
        options.put("generateThumbnail", true);
        request.setOptions(options);

        ForgeJobResponse response = forgeClient.submitJob(request);
        if (response == null || !response.isAccepted()) {
            markFailed(record, "SUBMIT_REJECTED",
                    response == null ? "转换器不可用（未部署或地址不通）" : response.getMessage());
            return;
        }
        record.setForgeJobId(response.getJobId());
        record.setStatus(CadVisualization.STATUS_QUEUED);
        record.setProgress(0);
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
        log.info("转换任务已提交: oid={}, jobId={}", record.getOid(), response.getJobId());
    }

    /**
     * 组装源件下载地址 —— 转换器取源的第 3 条路（完全无共享存储）。
     *
     * <p>主机取自 {@code callbackUrl}：它配的本来就是"转换器能访问到 CK-PLM 的地址"，
     * 与源件下载是同一个方向、同一台机器，没必要再让运维配第二个地址
     * （多一个配置项就多一处"改了这边忘了那边"）。
     *
     * <p>签不出地址时返回 null 而不是报错：转换器还能走 sourcePath / sourceRelativePath，
     * 少一条备用路径不该让整个提交失败。
     */
    private String buildSourceUrl(String visOid) {
        String callback = props.getCallbackUrl();
        String token = props.getCallbackToken();
        if (callback == null || callback.isBlank() || token == null || token.isBlank()) {
            return null;
        }
        String origin = originOf(callback);
        if (origin == null) {
            log.warn("回调地址解析不出主机，跳过源件下载地址: {}", callback);
            return null;
        }
        long exp = System.currentTimeMillis() / 1000L
                + Math.max(1, props.getSourceUrlTtlMinutes()) * 60L;
        String sig = SourceUrlSigner.sign(token, visOid, exp);
        return origin + "/api/internal/cad/source/" + visOid + "?exp=" + exp + "&sig=" + sig;
    }

    /** 取 {@code scheme://host[:port]} —— 去掉路径部分 */
    private static String originOf(String url) {
        try {
            URI u = URI.create(url);
            if (u.getScheme() == null || u.getHost() == null) {
                return null;
            }
            return u.getScheme() + "://" + u.getAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 回调 ====================

    @Override
    public void handleCallback(ForgeCallbackPayload payload) {
        if (payload == null) return;

        CadVisualization record = payload.getExternalId() != null
                ? visMapper.selectByOid(payload.getExternalId())
                : null;
        if (record == null && payload.getJobId() != null) {
            // 回调没带 externalId（或记录被重建）时，按转换器任务号兜底路由
            record = visMapper.selectByForgeJobId(payload.getJobId());
        }
        if (record == null) {
            log.warn("收到未知转换回调，已忽略: externalId={}, jobId={}",
                    payload.getExternalId(), payload.getJobId());
            return;
        }

        String status = payload.getStatus() == null ? "" : payload.getStatus().toUpperCase();

        // 过程进度与终态结果是两次独立请求（甚至可能来自不同线程），网络不保证顺序。
        boolean terminal = CadVisualization.STATUS_SUCCESS.equals(record.getStatus())
                || CadVisualization.STATUS_FAILED.equals(record.getStatus())
                || CadVisualization.STATUS_SKIPPED.equals(record.getStatus())
                // 取消同样是终态：转换器可能已经跑完并在路上发 SUCCESS，
                // 但用户明确按了"取消"，界面就该停在取消 —— 否则点了没反应，比没这个按钮更糟
                || CadVisualization.STATUS_CANCELLED.equals(record.getStatus());

        // 终态一旦落定就<b>任何回调都不再改写</b>（包括迟到的 SUCCESS / FAILED，不只是进度）。
        // 实测教训：取消 RUNNING 任务后，转换器杀掉子进程会立刻回一条 FAILED，
        // 只挡 RUNNING 的话，用户刚点的"取消"会在 1 秒内变成"失败" —— 看起来像没生效。
        if (terminal) {
            log.debug("忽略迟到的回调: oid={}, 当前状态={}, 回调状态={}",
                    record.getOid(), record.getStatus(), status);
            return;
        }

        // 进度上报：只更新进度与阶段，不改变终态判定
        if ("RUNNING".equals(status) || "QUEUED".equals(status)) {
            record.setStatus(CadVisualization.STATUS_RUNNING);
            record.setProgress(payload.getProgress());
            record.setStage(payload.getStage());
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
            return;
        }

        if ("SUCCESS".equals(status)) {
            applyArtifacts(record, payload);
            applyMetrics(record, payload.getMetrics());
            record.setStatus(CadVisualization.STATUS_SUCCESS);
            record.setProgress(100);
            // 完成态不再有"当前阶段"；留着最后一句会让已完成的任务看起来还在跑
            record.setStage(null);
            record.setConvertedAt(LocalDateTime.now());
            record.setErrorCode(null);
            record.setErrorMessage(null);
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
            log.info("转换成功: oid={}, 三角面={}, 零件数={}",
                    record.getOid(), record.getTriangleCount(), record.getPartCount());
            return;
        }

        // 失败：累计尝试次数，触顶即熔断（SKIPPED 不再重试）
        int attempts = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
        record.setAttemptCount(attempts);
        record.setErrorCode(payload.getErrorCode());
        record.setErrorMessage(payload.getErrorMessage());
        record.setStatus(attempts >= props.getMaxAttempts()
                ? CadVisualization.STATUS_SKIPPED
                : CadVisualization.STATUS_FAILED);
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
        log.warn("转换失败: oid={}, attempts={}, err={}", record.getOid(), attempts, payload.getErrorMessage());
    }

    // ==================== 查询 / 重试 ====================

    @Override
    public CadVisualization getByOid(String oid) {
        return visMapper.selectByOid(oid);
    }

    @Override
    public CadVisualization getLatest(String engDocIterationOid) {
        return visMapper.selectLatestByIteration(engDocIterationOid);
    }

    @Override
    public List<ContainerCadModelVO> listModelsByContainer(String containerOid) {
        if (containerOid == null || containerOid.isBlank()) {
            return List.of();
        }
        return visMapper.selectModelsByContainer(containerOid);
    }

    @Override
    public ContainerCadModelVO getModelByPartIteration(String partIterationOid) {
        if (partIterationOid == null || partIterationOid.isBlank()) {
            return null;
        }
        return visMapper.selectModelByPartIteration(partIterationOid.trim());
    }

    /**
     * 打包下载本容器下的全部数模源文件。
     *
     * <p>为什么不在前端逐个 {@code window.open}：浏览器会把连续弹窗当弹窗广告拦掉，
     * 用户最后拿到几个算几个 —— 这种"看起来跑了但少了一半文件"最难排查。
     *
     * <p>整包在内存里拼：数模是几 MB 量级、一次最多几十份，直写响应流反而
     * 要处理客户端中断后的半截 zip；先拼后写能保证"要么完整、要么 404"。
     */
    @Override
    public byte[] packageModels(String containerOid) {
        if (containerOid == null || containerOid.isBlank()) {
            return null;
        }
        List<ContainerCadModelVO> models = visMapper.selectModelsByContainer(containerOid);
        if (models == null || models.isEmpty()) {
            return null;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Set<String> usedNames = new HashSet<>();
        int added = 0;
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (ContainerCadModelVO model : models) {
                if (model.getSourceCkfileOid() == null) continue;
                CKFile file = ckFileMapper.selectByOid(model.getSourceCkfileOid());
                if (file == null) continue;
                Path path = fileStorage.resolvePath(file.getStoragePath(), file.getTenantOid());
                if (path == null || !Files.isRegularFile(path)) continue;
                zip.putNextEntry(new ZipEntry(uniqueEntryName(model, file, usedNames)));
                Files.copy(path, zip);
                zip.closeEntry();
                added++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("打包数模失败: " + e.getMessage(), e);
        }
        return added == 0 ? null : buffer.toByteArray();
    }

    /**
     * zip 内条目名：工程数据编码 + 原文件名。
     *
     * <p>带编码前缀是因为不同工程数据下常常出现同名文件（{@code body.stp} 遍地都是），
     * 解压到一个目录就会互相覆盖。真撞名时补序号，绝不静默丢文件。
     */
    private String uniqueEntryName(ContainerCadModelVO model, CKFile file, Set<String> used) {
        String prefix = model.getEngDocNumber() == null || model.getEngDocNumber().isBlank()
                ? "" : model.getEngDocNumber() + "-";
        String base = prefix + (file.getFileName() == null || file.getFileName().isBlank()
                ? "model" : file.getFileName());
        String name = base;
        int seq = 2;
        while (!used.add(name)) {
            int dot = base.lastIndexOf('.');
            name = dot > 0
                    ? base.substring(0, dot) + "(" + seq + ")" + base.substring(dot)
                    : base + "(" + seq + ")";
            seq++;
        }
        return name;
    }

    @Override
    public CadVisualization retry(String oid) {
        CadVisualization record = visMapper.selectByOid(oid);
        if (record == null) {
            throw new IllegalArgumentException("转换记录不存在：" + oid);
        }
        if (!props.isEnabled()) {
            throw new IllegalStateException("CAD 可视化未启用");
        }
        // 重置熔断计数，允许用户手动再试一次（用户主动点是明确意图，不该被熔断挡住）
        record.setAttemptCount(0);
        record.setStatus(CadVisualization.STATUS_QUEUED);
        // 上一轮的进度与阶段必须清掉，否则新任务一开始就显示"上次卡在 85%"
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

    // ==================== 批量提交 / 取消 ====================

    /**
     * 批量提交上限。
     *
     * <p>不设上限的话，一次勾 2000 条会让请求线程跑十几分钟（每条都要读文件算 SHA1），
     * 网关早就超时了，用户却看到浏览器一直转。宁可直接拒绝并让他分批提交。
     */
    private static final int MAX_BATCH = 200;

    @Override
    public CadConvertBatchResultVO submitBatch(List<CadConvertBatchItem> items) {
        CadConvertBatchResultVO result = new CadConvertBatchResultVO();
        if (items == null || items.isEmpty()) {
            result.setTotal(0);
            return result;
        }
        if (items.size() > MAX_BATCH) {
            throw new IllegalArgumentException("一次最多提交 " + MAX_BATCH + " 份数模，请分批提交");
        }

        int accepted = 0, cached = 0, skipped = 0, failed = 0;
        for (CadConvertBatchItem item : items) {
            CadConvertBatchResultVO.Item row = new CadConvertBatchResultVO.Item();
            row.setIterationOid(item == null ? null : item.getIterationOid());
            row.setCkfileOid(item == null ? null : item.getCkfileOid());
            try {
                CadVisualization vis = submit(row.getIterationOid(), row.getCkfileOid());
                row.setVisOid(vis.getOid());
                row.setStatus(vis.getStatus());
                String status = vis.getStatus();
                if (CadVisualization.STATUS_SUCCESS.equals(status)) {
                    // 内容指纹命中：复用了上次产物，没白烧 CPU
                    row.setResult("CACHED");
                    cached++;
                } else if (CadVisualization.STATUS_SKIPPED.equals(status)) {
                    row.setResult("SKIPPED");
                    row.setMessage(vis.getErrorMessage());
                    skipped++;
                } else if (CadVisualization.STATUS_FAILED.equals(status)) {
                    row.setResult("FAILED");
                    row.setMessage(vis.getErrorMessage());
                    failed++;
                } else {
                    row.setResult("ACCEPTED");
                    accepted++;
                }
            } catch (Exception e) {
                // 一条坏数据不该毁掉整批：记下来，继续下一条
                log.warn("批量提交单条失败: iteration={}, ckfile={}, err={}",
                        row.getIterationOid(), row.getCkfileOid(), e.getMessage());
                row.setResult("FAILED");
                row.setMessage(e.getMessage());
                failed++;
            }
            result.getItems().add(row);
        }

        result.setTotal(items.size());
        result.setAccepted(accepted);
        result.setCached(cached);
        result.setSkipped(skipped);
        result.setFailed(failed);
        log.info("批量提交转换: 共 {} 条，入队 {}，命中缓存 {}，跳过 {}，失败 {}",
                result.getTotal(), accepted, cached, skipped, failed);
        return result;
    }

    @Override
    public CadVisualization cancel(String oid) {
        CadVisualization record = visMapper.selectByOid(oid);
        if (record == null) {
            throw new IllegalArgumentException("转换记录不存在：" + oid);
        }
        boolean inFlight = CadVisualization.STATUS_QUEUED.equals(record.getStatus())
                || CadVisualization.STATUS_RUNNING.equals(record.getStatus());
        if (!inFlight) {
            throw new IllegalStateException("只有排队中 / 转换中的任务可以取消（当前状态：" + record.getStatus() + "）");
        }

        // 先让转换器停手（尽力而为）：不通知的话，已排队的任务照样会占满 worker
        if (record.getForgeJobId() != null) {
            forgeClient.cancelJob(record.getForgeJobId());
        }

        record.setStatus(CadVisualization.STATUS_CANCELLED);
        // 取消后没有"当前阶段"可言；但保留错误码，便于事后分辨"人取消"与"机器失败"
        record.setStage(null);
        record.setErrorCode("CANCELLED");
        record.setErrorMessage("已被用户取消");
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
        log.info("转换任务已取消: oid={}, jobId={}", oid, record.getForgeJobId());
        return record;
    }

    @Override
    public boolean isAvailable() {
        return props.isEnabled() && props.getBaseUrl() != null && !props.getBaseUrl().isBlank();
    }

    // ==================== 转换中心 ====================

    /** 分页上限：再大用户也不会一页看几百行，反而把一次查询拖成慢请求 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 上一次看门狗真正扫描的时间戳（见 {@link #watchdogScan()} 的节拍说明） */
    private volatile long lastWatchdogAt = 0L;

    @Override
    public CadConvertJobPageVO listConvertJobs(String filter, String keyword, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(1, page);
        List<String> statuses = statusesOf(filter);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();

        CadConvertJobPageVO vo = new CadConvertJobPageVO();
        vo.setList(visMapper.selectConvertJobs(statuses, kw, (safePage - 1) * safeSize, safeSize));
        vo.setTotal(visMapper.countConvertJobs(statuses, kw));
        vo.setStats(visMapper.selectConvertStats());
        vo.setPage(safePage);
        vo.setSize(safeSize);
        return vo;
    }

    /**
     * 页签 → 状态集合。
     *
     * <p>{@code FAILED} 与 {@code TIMEOUT} 归一类（都是"可以再试一次"），
     * {@code SKIPPED} 单独一类（重试无意义，见 {@link CadConvertStatsVO} 的说明）。
     * 空 / 未知过滤词一律当"全部"，不抛异常 —— 前端拼错参数不该让页面 500。
     */
    private List<String> statusesOf(String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return null;
        }
        return switch (filter.toLowerCase()) {
            case "active" -> List.of(CadVisualization.STATUS_QUEUED, CadVisualization.STATUS_RUNNING);
            case "success" -> List.of(CadVisualization.STATUS_SUCCESS);
            case "failed" -> List.of(CadVisualization.STATUS_FAILED, CadVisualization.STATUS_TIMEOUT);
            // "不再处理"一类：转换器判定不该转（SKIPPED）+ 用户自己叫停（CANCELLED）。
            // 两者都不能靠"看进度"消失，必须有一个入口能找到它们
            case "skipped" -> List.of(CadVisualization.STATUS_SKIPPED, CadVisualization.STATUS_CANCELLED);
            default -> null;
        };
    }

    // ==================== 看门狗 ====================

    /**
     * 补偿通道：扫描超时未回的任务，主动向转换器要状态。
     *
     * <p>为什么必须有：回调依赖网络与转换器生命周期，丢一次（重启、网络抖动、
     * 回调地址配错）任务就永远停在 RUNNING，前端一直转圈。看门狗把它拉回终态。
     */
    @Override
    /**
     * 看门狗节拍。
     *
     * <p>扫描间隔已改为库里的配置项（页面可改），而 {@code @Scheduled} 的 SpEL
     * 在启动时就固化成常量 —— 所以这里按固定节拍唤醒，未到配置的间隔就直接返回。
     * 好处是改间隔不用重启；代价是唤醒次数固定（15 秒一次空转，成本可忽略）。
     */
    @Scheduled(fixedDelay = 15_000)
    public void watchdogScan() {
        if (!props.isEnabled() || !props.isWatchdogEnabled()) return;

        long interval = Math.max(10_000L, props.getWatchdogIntervalMs());
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastWatchdogAt < interval) return;
        lastWatchdogAt = nowMs;

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(props.getStaleAfterMinutes());
        List<CadVisualization> stale;
        try {
            stale = visMapper.selectStaleInFlight(cutoff, props.getWatchdogBatchSize());
        } catch (Exception e) {
            log.warn("扫描超时转换任务失败: {}", e.getMessage());
            return;
        }
        if (stale == null || stale.isEmpty()) return;

        for (CadVisualization record : stale) {
            try {
                compensate(record);
            } catch (Exception e) {
                log.warn("补偿转换任务异常: oid={}, err={}", record.getOid(), e.getMessage());
            }
        }
    }

    private void compensate(CadVisualization record) {
        if (record.getForgeJobId() == null) {
            // 连任务号都没有：提交就没成功过
            markFailed(record, "NEVER_SUBMITTED", "任务从未成功提交给转换器");
            return;
        }
        Map<String, Object> status = forgeClient.queryJob(record.getForgeJobId());
        if (status == null) {
            int attempts = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
            record.setAttemptCount(attempts);
            if (attempts >= props.getMaxAttempts()) {
                record.setStatus(CadVisualization.STATUS_SKIPPED);
                record.setErrorCode("FORGE_UNREACHABLE");
                record.setErrorMessage("转换器查询不到该任务（可能已重启），已停止重试");
            } else {
                record.setStatus(CadVisualization.STATUS_FAILED);
                record.setErrorCode("FORGE_UNREACHABLE");
                record.setErrorMessage("转换器查询不到该任务");
            }
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
            return;
        }

        Object st = status.get("status");
        String s = st == null ? "" : String.valueOf(st).toUpperCase();
        if ("SUCCESS".equals(s)) {
            // 回调丢了但产物已生成：按回调同样的逻辑收下来
            ForgeCallbackPayload payload = new ForgeCallbackPayload();
            payload.setExternalId(record.getOid());
            payload.setJobId(record.getForgeJobId());
            payload.setStatus("SUCCESS");
            handleCallback(payload);
        } else if ("FAILED".equals(s)) {
            Object err = status.get("errorMessage");
            ForgeCallbackPayload payload = new ForgeCallbackPayload();
            payload.setExternalId(record.getOid());
            payload.setJobId(record.getForgeJobId());
            payload.setStatus("FAILED");
            payload.setErrorCode("FORGE_FAILED");
            payload.setErrorMessage(err == null ? "转换器报告失败" : String.valueOf(err));
            handleCallback(payload);
        } else if ("RUNNING".equals(s) || "QUEUED".equals(s)) {
            // 还在跑：刷新进度与阶段即可，继续等下一轮
            Object p = status.get("progress");
            record.setProgress(p instanceof Number ? ((Number) p).intValue() : record.getProgress());
            Object stage = status.get("stage");
            if (stage != null) {
                record.setStage(String.valueOf(stage));
            }
            record.setStatus(CadVisualization.STATUS_RUNNING);
            record.setUpdatedAt(LocalDateTime.now());
            visMapper.update(record);
        } else {
            markFailed(record, "UNKNOWN_STATUS", "转换器返回未知状态：" + s);
        }
    }

    // ==================== 内部工具 ====================

    /** 前置校验：返回非空表示应当跳过，并给出原因 */
    private String evaluateSkipReason(CKFile source, String sha1) {
        if (!props.isEnabled()) {
            return "CAD 可视化未启用（未部署转换器）";
        }
        String name = source.getFileName() == null ? "" : source.getFileName().toLowerCase();
        List<String> exts = props.getSupportedExtensions();
        if (exts != null && !exts.isEmpty()) {
            boolean matched = false;
            for (String ext : exts) {
                if (name.endsWith(ext.toLowerCase())) { matched = true; break; }
            }
            if (!matched) {
                return "不支持的格式，仅支持：" + String.join(" / ", exts);
            }
        }
        long size = source.getFileSize() == null ? 0L : source.getFileSize();
        if (size > props.getMaxSourceSizeBytes()) {
            return "源文件超过大小上限（" + (props.getMaxSourceSizeBytes() / 1024 / 1024) + " MB）";
        }
        if (sha1 == null) {
            return "源文件不可读（已落库但磁盘上找不到）";
        }
        return null;
    }

    private CadVisualization persistSkipped(String iterationOid, CKFile source, String sha1,
                                            String tenantOid, String reason) {
        CadVisualization record = newRecord(iterationOid, source, sha1, tenantOid);
        record.setStatus(CadVisualization.STATUS_SKIPPED);
        record.setErrorCode("SKIPPED");
        record.setErrorMessage(reason);
        visMapper.insert(record);
        log.info("CAD 转换跳过: iteration={}, 原因={}", iterationOid, reason);
        return record;
    }

    private CadVisualization newRecord(String iterationOid, CKFile source, String sha1, String tenantOid) {
        LocalDateTime now = LocalDateTime.now();
        String user = UserContext.get();
        CadVisualization record = new CadVisualization();
        record.setOid(UUID.randomUUID().toString());
        record.setTenantOid(tenantOid != null ? tenantOid : source.getTenantOid());
        record.setEngDocIterationOid(iterationOid);
        record.setSourceCkfileOid(source.getOid());
        record.setSourceSha1(sha1);
        record.setSourceFileName(source.getFileName());
        record.setSourceSize(source.getFileSize());
        record.setStatus(CadVisualization.STATUS_QUEUED);
        record.setProgress(0);
        record.setAttemptCount(0);
        record.setSubmittedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setCreator(user);
        record.setUpdater(user);
        return record;
    }

    private void markFailed(CadVisualization record, String code, String message) {
        int attempts = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
        record.setAttemptCount(attempts);
        record.setErrorCode(code);
        record.setErrorMessage(message);
        record.setStatus(attempts >= props.getMaxAttempts()
                ? CadVisualization.STATUS_SKIPPED
                : CadVisualization.STATUS_FAILED);
        record.setUpdatedAt(LocalDateTime.now());
        visMapper.update(record);
    }

    /** 把产物登记成 CKFile，让下载/权限/清理统一走既有文件通道 */
    private void applyArtifacts(CadVisualization record, ForgeCallbackPayload payload) {
        if (payload.getArtifacts() == null) return;
        for (ForgeCallbackPayload.ArtifactItem item : payload.getArtifacts()) {
            String ckfileOid = registerArtifact(record, item);
            if (ckfileOid == null) continue;
            String kind = item.getKind() == null ? "" : item.getKind().toLowerCase();
            switch (kind) {
                case "glb"        -> record.setGlbCkfileOid(ckfileOid);
                case "structure"  -> record.setStructureCkfileOid(ckfileOid);
                case "thumbnail"  -> record.setThumbnailCkfileOid(ckfileOid);
                case "lod"        -> record.setLodCkfileOid(ckfileOid);
                default           -> log.warn("未知产物类型: {}", kind);
            }
        }
    }

    /**
     * 产物入库。
     *
     * <p>两条取数路径：优先读共享卷上的文件（零拷贝）；
     * 读不到（跨机部署、没挂共享卷）就走 HTTP 下载落临时文件。两种部署形态都能工作。
     *
     * <h3>为什么两条路都不经过 byte[]</h3>
     * 产物可能有几百 MB。同机部署时读共享卷那条路几乎不花钱，
     * 但分布式部署下<b>每次回传都要走 HTTP</b>，若先读成 byte[] 再入库，
     * 堆内存就随产物大小线性上涨 —— 而且是每个并发任务各来一份。
     * 所以一路流式：共享卷直接开流，HTTP 先落临时文件再开流，入库也是流式写。
     */
    private String registerArtifact(CadVisualization record, ForgeCallbackPayload.ArtifactItem item) {
        String fileName = item.getFileName() != null && !item.getFileName().isBlank()
                ? item.getFileName()
                : record.getOid() + "-" + item.getKind();

        // ---- 1) 共享卷：直接开流，不落中间文件 ----
        if (item.getPath() != null && !item.getPath().isBlank()) {
            try {
                Path p = Path.of(item.getPath());
                if (Files.isRegularFile(p) && Files.isReadable(p) && Files.size(p) > 0) {
                    try (InputStream in = Files.newInputStream(p)) {
                        return storeArtifact(record, item, fileName, in, Files.size(p));
                    }
                }
            } catch (Exception e) {
                log.debug("共享卷读取产物失败，改走 HTTP: path={}, err={}", item.getPath(), e.getMessage());
            }
        }

        // ---- 2) HTTP：流式落临时文件，入库后立刻删 ----
        if (record.getForgeJobId() != null) {
            Path tmp = forgeClient.downloadArtifactToTemp(record.getForgeJobId(), item.getKind());
            if (tmp != null) {
                try (InputStream in = Files.newInputStream(tmp)) {
                    long size = Files.size(tmp);
                    if (size <= 0) {
                        log.warn("产物为空，跳过登记: kind={}, oid={}", item.getKind(), record.getOid());
                        return null;
                    }
                    return storeArtifact(record, item, fileName, in, size);
                } catch (Exception e) {
                    log.error("登记产物失败: kind={}, oid={}", item.getKind(), record.getOid(), e);
                    return null;
                } finally {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException ignored) {
                        // 临时文件删不掉不值得惊动日志：系统临时目录由运维统一清理
                    }
                }
            }
        }
        log.warn("产物为空，跳过登记: kind={}, oid={}", item.getKind(), record.getOid());
        return null;
    }

    /** 流式写入文件存储并登记成 CKFile。size 由调用方给出（已在落盘前量过） */
    private String storeArtifact(CadVisualization record, ForgeCallbackPayload.ArtifactItem item,
                                 String fileName, InputStream in, long size) throws IOException {
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

    private void applyMetrics(CadVisualization record, ForgeCallbackPayload.Metrics metrics) {
        if (metrics == null) return;
        record.setUnits(metrics.getUnits());
        record.setTriangleCount(metrics.getTriangleCount());
        record.setPartCount(metrics.getPartCount());
        record.setOutputSize(metrics.getOutputSize());
        record.setDurationMs(metrics.getDurationMs());
        record.setConverterVersion(metrics.getConverterVersion());
    }

    /** 文件内容 SHA-1；读不到返回 null（后续按"无法去重"处理，不阻断流程） */
    private String sha1Of(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16))
                  .append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("计算文件指纹失败: path={}, err={}", path, e.getMessage());
            return null;
        }
    }
}
