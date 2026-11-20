/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.controller;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.mapper.CKFileMapper;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.cad.util.SourceUrlSigner;
import cn.ck.plm.drawing.config.DrawingForgeProperties;
import cn.ck.plm.drawing.entity.DrawingVisualization;
import cn.ck.plm.drawing.mapper.DrawingVisualizationMapper;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 二维源件下载 —— CK-DrawingForge 在无共享存储时回拉 DWG/DXF 的通道。
 *
 * <p>调用方是转换器进程（无用户会话），故 /api/internal/drawing/** 须在 AuthConfig 放行，
 * 这里自己校验两道：X-Cad-Forge-Token（常量时间）+ sig/exp（签名与有效期）。
 * 按转换记录 oid 取（非文件 oid），天然挡住"换 oid 拖走别人图档"。流式传输，内存无关文件大小。
 */
@RestController
@RequestMapping("/api/internal/drawing")
public class DrawingSourceController {

    private static final Logger log = LoggerFactory.getLogger(DrawingSourceController.class);

    private final DrawingVisualizationMapper visMapper;
    private final CKFileMapper ckFileMapper;
    private final FileStorageService fileStorage;
    private final DrawingForgeProperties properties;

    public DrawingSourceController(DrawingVisualizationMapper visMapper,
                                   CKFileMapper ckFileMapper,
                                   FileStorageService fileStorage,
                                   DrawingForgeProperties properties) {
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
            log.warn("拒绝未授权的二维源件下载: 令牌为空或不匹配");
            return ResponseEntity.status(401).body(ApiResponse.fail(401, "未授权的源件下载"));
        }
        if (!SourceUrlSigner.verify(expected, visOid, exp, sig)) {
            log.warn("拒绝无效的二维源件下载地址: visOid={}", visOid);
            return ResponseEntity.status(401).body(ApiResponse.fail(401, "源件地址无效或已过期"));
        }

        DrawingVisualization record = visMapper.selectByOid(visOid);
        if (record == null || record.getSourceCkfileOid() == null) {
            return ResponseEntity.status(404).body(ApiResponse.fail(404, "转换记录不存在"));
        }
        CKFile file = ckFileMapper.selectByOid(record.getSourceCkfileOid());
        if (file == null) {
            return ResponseEntity.status(404).body(ApiResponse.fail(404, "源文件不存在"));
        }
        Path path = fileStorage.resolvePath(file.getStoragePath(), file.getTenantOid());
        if (path == null || !Files.isRegularFile(path)) {
            log.warn("二维源文件不可读: ckfile={}, path={}", file.getOid(), path);
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
                ? "source.dwg" : file.getFileName();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8))
                .contentLength(size)
                .body(body);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
