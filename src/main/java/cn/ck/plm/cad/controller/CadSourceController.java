/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.controller;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.mapper.CKFileMapper;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.cad.config.CadForgeProperties;
import cn.ck.plm.cad.entity.CadVisualization;
import cn.ck.plm.cad.mapper.CadVisualizationMapper;
import cn.ck.plm.cad.util.SourceUrlSigner;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 源件下载 —— 转换器在<b>没有共享存储</b>时回拉 STEP 的通道。
 *
 * <h3>为什么需要它</h3>
 * 同机部署时转换器直接读磁盘；一旦分布式部署又没挂共享卷，它就<b>根本读不到源件</b>，
 * 任务会以"源文件不在本机"失败。中小企业大多就是两台机器各挂各的盘，
 * 不能把共享存储当成使用前提 —— 于是给一条网络回拉的路。
 *
 * <h3>为什么不在登录拦截器里</h3>
 * 与 {@link CadConvertCallbackController} 同理：调用方是转换器进程，没有用户会话。
 * 路径前缀 {@code /api/internal/cad/**} 已在 {@code AuthConfig} 里放行，
 * 所以<b>这里必须自己校验</b>，且是两道：
 * <ol>
 *   <li>{@code X-Cad-Forge-Token} —— 证明调用方是转换器（常量时间比较）；</li>
 *   <li>{@code sig} + {@code exp} —— 证明这个地址是我们签发的、且还没过期。</li>
 * </ol>
 *
 * <h3>为什么按转换记录 oid 取、而不是按文件 oid</h3>
 * 按记录取要反查 {@code ck_cad_visualization → source_ckfile_oid}，
 * 于是"换个 oid 拖走别人的图档"这条路本身就走不通 ——
 * 知道的 oid 只有我们签发出去的那些。
 *
 * <h3>为什么必须流式</h3>
 * 几百 MB 的 STEP 读进内存再吐出去，会把 JVM 堆顶穿。
 * 这里直接 {@code InputStream.transferTo}，内存占用与文件大小无关。
 */
@RestController
@RequestMapping("/api/internal/cad")
public class CadSourceController {

    private static final Logger log = LoggerFactory.getLogger(CadSourceController.class);

    private final CadVisualizationMapper visMapper;
    private final CKFileMapper ckFileMapper;
    private final FileStorageService fileStorage;
    private final CadForgeProperties properties;

    public CadSourceController(CadVisualizationMapper visMapper,
                               CKFileMapper ckFileMapper,
                               FileStorageService fileStorage,
                               CadForgeProperties properties) {
        this.visMapper = visMapper;
        this.ckFileMapper = ckFileMapper;
        this.fileStorage = fileStorage;
        this.properties = properties;
    }

    @GetMapping("/source/{visOid}")
    public ResponseEntity<Object> source(@PathVariable("visOid") String visOid,
                                         @RequestParam(value = "exp", required = false) String exp,
                                         @RequestParam(value = "sig", required = false) String sig,
                                         @RequestHeader(value = "X-Cad-Forge-Token", required = false) String token) {
        String expected = properties.getCallbackToken();
        if (expected == null || expected.isBlank() || !constantTimeEquals(expected, token)) {
            log.warn("拒绝未授权的源件下载: 令牌为空或不匹配");
            return ResponseEntity.status(401).body(ApiResponse.fail(401, "未授权的源件下载"));
        }
        if (!SourceUrlSigner.verify(expected, visOid, exp, sig)) {
            log.warn("拒绝无效的源件下载地址: visOid={}（签名不匹配或已过期）", visOid);
            return ResponseEntity.status(401).body(ApiResponse.fail(401, "源件地址无效或已过期"));
        }

        CadVisualization record = visMapper.selectByOid(visOid);
        if (record == null || record.getSourceCkfileOid() == null) {
            return ResponseEntity.status(404).body(ApiResponse.fail(404, "转换记录不存在"));
        }
        CKFile file = ckFileMapper.selectByOid(record.getSourceCkfileOid());
        if (file == null) {
            return ResponseEntity.status(404).body(ApiResponse.fail(404, "源文件不存在"));
        }
        Path path = fileStorage.resolvePath(file.getStoragePath(), file.getTenantOid());
        if (path == null || !Files.isRegularFile(path)) {
            log.warn("源文件不可读: ckfile={}, path={}", file.getOid(), path);
            return ResponseEntity.status(404).body(ApiResponse.fail(404, "源文件不可读"));
        }

        long size;
        try {
            size = Files.size(path);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.fail(500, "读取源文件失败"));
        }

        StreamingResponseBody body = out -> {
            try (InputStream in = Files.newInputStream(path)) {
                in.transferTo(out);
            }
        };
        String name = file.getFileName() == null || file.getFileName().isBlank()
                ? "source.step" : file.getFileName();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8))
                .contentLength(size)
                .body(body);
    }

    /** 常量时间字符串比较，避免通过响应时间侧信道泄漏令牌前缀 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
