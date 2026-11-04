/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.loader;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 行业包加载器 —— 扫描包目录，把 {@code pack.json} 解析成 {@link PackSource}。
 *
 * <h3>包从哪来（两处，外部优先）</h3>
 * <ol>
 *   <li><b>平台内置</b>：{@code classpath:industry-packs/<pack>/<version>/pack.json}
 *       —— 随 jar 发布，开箱即用（platform-base / electronics-core / smart-hardware-ipd …）；</li>
 *   <li><b>外部目录</b>：{@code plm.industry-pack.dir}（可选）—— 实施顾问换包不必改 jar；
 *       同一 {@code code@version} 时<b>外部优先</b>，便于现场修正内置包的表述。</li>
 * </ol>
 *
 * <h3>为什么加载失败只告警不抛异常</h3>
 * 包是数据：某个包写坏了不该让整个应用起不来（与初始化器"失败只告警"的口径一致）。
 * 坏包会在 {@code /api/industry-packs} 的校验结果里显形，而不是变成一次启动失败。
 */
@Component
public class IndustryPackLoader {

    private static final Logger log = LoggerFactory.getLogger(IndustryPackLoader.class);

    /** 内置包扫描表达式：industry-packs/<pack>/<version>/pack.json */
    private static final String CLASSPATH_PATTERN = "classpath*:industry-packs/*/*/pack.json";

    /** classpath 内包根目录 —— 定位包内其它文件时的锚点 */
    private static final String CLASSPATH_ROOT = "industry-packs/";

    private static final String MANIFEST_FILE = "pack.json";
    private static final String CAPABILITIES_FILE = "capabilities.json";

    private final ObjectMapper objectMapper;

    /** 外部包目录（可空）：{@code plm.industry-pack.dir} */
    private final String externalDir;

    /** 已加载的包（懒加载缓存；见 {@link #loadAll()} 的说明） */
    private volatile List<PackSource> cachedPacks;

    /** {@code code@version} → 包 的索引 */
    private volatile Map<String, PackSource> cachedIndex;

    public IndustryPackLoader(ObjectMapper objectMapper,
                              @Value("${plm.industry-pack.dir:}") String externalDir) {
        this.objectMapper = objectMapper;
        this.externalDir = externalDir == null ? "" : externalDir.trim();
    }

    /**
     * 加载全部可见包，按 {@code code@version} 去重（外部覆盖内置），顺序稳定便于日志对账。
     *
     * <p>结果<b>懒加载 + 缓存</b>：包目录在一次运行内不变，而引擎每次干活都会找包
     * （装父包、查清单、预演…）—— 不加缓存就会反复扫 classpath、反复算内容指纹
     * （启动日志里"行业包加载完成"重复出现就是这么来的）。换包后需要生效时调 {@link #reload()}。
     */
    public List<PackSource> loadAll() {
        List<PackSource> cached = this.cachedPacks;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.cachedPacks == null) {
                Map<String, PackSource> merged = new LinkedHashMap<>();

                // 1) 内置包
                for (PackSource source : loadClasspathPacks()) {
                    merged.put(source.getId(), source);
                }
                // 2) 外部目录（同 id 覆盖内置）
                int builtIn = merged.size();
                for (PackSource source : loadExternalPacks()) {
                    if (merged.containsKey(source.getId())) {
                        log.info("行业包 {} 使用外部目录版本（覆盖内置包）", source.getId());
                    }
                    merged.put(source.getId(), source);
                }

                List<PackSource> all = new ArrayList<>(merged.values());
                all.sort(Comparator.comparing(PackSource::getId));
                log.info("行业包加载完成: {} 个（内置 {} + 外部覆盖 {}）",
                        all.size(), builtIn, all.size() - builtIn);

                Map<String, PackSource> index = new LinkedHashMap<>();
                for (PackSource source : all) {
                    index.put(source.getId(), source);
                }
                this.cachedPacks = all;
                this.cachedIndex = index;
            }
            cached = this.cachedPacks;
        }
        return cached;
    }

    /** 按 {@code code@version} 找包（走索引，O(1)） */
    public PackSource find(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        loadAll();
        Map<String, PackSource> index = this.cachedIndex;
        return index == null ? null : index.get(id.trim());
    }

    /** 清缓存 —— 外部目录换包/调试时热加载用（下次 {@link #loadAll()} 重新扫描） */
    public synchronized void reload() {
        this.cachedPacks = null;
        this.cachedIndex = null;
        log.info("行业包缓存已清空，下次访问将重新扫描包目录");
    }

    // ==================== 内置包（classpath） ====================

    private List<PackSource> loadClasspathPacks() {
        List<PackSource> list = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CLASSPATH_PATTERN);
            for (Resource manifestResource : resources) {
                // basePath 必须是「classpath 内路径」（industry-packs/<pack>/<version>/），
                // 不能直接用资源 URL：开发期是 file:/…/target/classes/…，打包后是
                // jar:file:…!/BOOT-INF/classes/… —— 把前缀带进 ClassPathResource 会让 exists() 恒为 false，
                // 于是 capabilities.json 与包内模板文件被静默跳过（能力开关曾全为空就是这么来的）。
                String url = manifestResource.getURL().toString();
                int marker = url.indexOf(CLASSPATH_ROOT);
                String path = marker >= 0 ? url.substring(marker) : url;
                // 注意：要截到「版本目录」这一层（去掉 pack.json 本身），
                // 否则 basePath 会变成 "…/pack.json"，拼出来的包内文件路径必然读不到。
                int cut = path.lastIndexOf('/');
                String basePath = cut > 0 ? path.substring(0, cut + 1) : path;
                PackSource source = parse(basePath, "classpath", manifestResource, null);
                if (source != null) {
                    list.add(source);
                }
            }
        } catch (IOException e) {
            log.warn("扫描内置行业包失败: {}", e.getMessage());
        }
        return list;
    }

    // ==================== 外部目录 ====================

    private List<PackSource> loadExternalPacks() {
        List<PackSource> list = new ArrayList<>();
        if (externalDir.isEmpty()) {
            return list;
        }
        Path root = Paths.get(externalDir);
        if (!Files.isDirectory(root)) {
            log.warn("plm.industry-pack.dir 配置的目录不存在，忽略: {}", externalDir);
            return list;
        }
        try (Stream<Path> packs = Files.walk(root, 3)) {
            packs.filter(p -> p.getFileName().toString().equals(MANIFEST_FILE) && Files.isRegularFile(p))
                    .forEach(p -> {
                        Path dir = p.getParent();
                        try {
                            PackSource source = parse(
                                    dir.toString(), "file", null, dir.resolve(MANIFEST_FILE));
                            if (source != null && source.getId() != null) {
                                list.add(source);
                            }
                        } catch (Exception e) {
                            log.warn("解析外部行业包失败 {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描外部行业包目录失败 {}: {}", externalDir, e.getMessage());
        }
        return list;
    }

    // ==================== 解析 ====================

    private PackSource parse(String basePath, String kind, Resource classpathManifest, Path fileManifest) {
        try {
            byte[] manifestBytes = classpathManifest != null
                    ? readAll(classpathManifest.getInputStream())
                    : Files.readAllBytes(fileManifest);

            PackManifest manifest = objectMapper.readValue(manifestBytes, PackManifest.class);
            if (manifest == null || manifest.getCode() == null || manifest.getVersion() == null) {
                log.warn("行业包清单缺少 code/version，跳过: {}", basePath);
                return null;
            }
            manifest.setBasePath(basePath);

            // 能力开关（可选文件）
            Map<String, Object> capabilities = Map.of();
            byte[] capabilityBytes = kind.equals("classpath")
                    ? tryReadClasspath(basePath + CAPABILITIES_FILE)
                    : tryReadFile(basePath, CAPABILITIES_FILE);
            if (capabilityBytes != null && capabilityBytes.length > 0) {
                capabilities = objectMapper.readValue(capabilityBytes, Map.class);
            }

            // 清单指纹：pack.json + capabilities.json 的字节摘要（升级 diff 与"内容变了没"的判据）
            manifest.setContentHash(sha256(manifestBytes, capabilityBytes == null ? new byte[0] : capabilityBytes));

            PackSource.Locator locator = kind.equals("classpath")
                    ? new ClasspathLocator(basePath)
                    : new FileLocator(basePath);

            PackSource source = new PackSource(manifest, capabilities, locator);
            // 载入诊断：包内文件定位出问题时会静默丢内容（能力开关、模板文件），所以留一行 INFO 便于对账
            log.info("已加载行业包: {}（资源声明 {} 个，能力开关 {} 项，基准路径 {}，能力文件 {}）",
                    source.getId(), manifest.getResources().size(),
                    capabilities.isEmpty() ? 0
                            : ((java.util.List<?>) capabilities.getOrDefault("capabilities", java.util.List.of())).size(),
                    basePath, capabilityBytes == null ? "未读到" : "已读到");
            return source;
        } catch (Exception e) {
            // 用 error 而不是 warn：包被静默丢掉会引发一串"看不懂"的下游症状 ——
            // 典型是它的子包报「父包不存在」（如 platform-base 的 JSON 多了一对引号，
            // 导致 automotive-apqp / military-gjb / traditional-stage-gate 三个包全部装不上），
            // 而包列表里那个包"凭空消失"。这里把后果直接写在日志里，省得下次再从现象倒推。
            log.error("解析行业包失败，该包已被跳过（它的子包会报\"父包不存在\"）: {} — {}", basePath, e.getMessage());
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            return stream.readAllBytes();
        }
    }

    private byte[] tryReadClasspath(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.exists() ? readAll(resource.getInputStream()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] tryReadFile(String basePath, String relative) {
        try {
            Path p = Paths.get(basePath).resolve(relative);
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== Locator 实现 ====================

    /** classpath 包的定位器：{@code basePath + relativeFile} */
    private static class ClasspathLocator implements PackSource.Locator {

        private final String basePath;

        /** 列目录用（内置包在 jar 里也能枚举，故走 pattern resolver 而不是 File） */
        private static final PathMatchingResourcePatternResolver RESOLVER =
                new PathMatchingResourcePatternResolver();

        ClasspathLocator(String basePath) {
            this.basePath = basePath;
        }

        @Override
        public InputStream open(String relativeFile) throws IOException {
            return new ClassPathResource(basePath + relativeFile).getInputStream();
        }

        @Override
        public boolean exists(String relativeFile) {
            return new ClassPathResource(basePath + relativeFile).exists();
        }

        @Override
        public List<String> list(String relativeDir) {
            try {
                Resource[] resources = RESOLVER.getResources(
                        "classpath*:" + basePath + trimSlash(relativeDir) + "/*");
                List<String> names = new ArrayList<>();
                for (Resource resource : resources) {
                    if (resource.getFilename() != null) {
                        names.add(resource.getFilename());
                    }
                }
                names.sort(Comparator.naturalOrder());
                return names;
            } catch (IOException e) {
                return List.of();
            }
        }
    }

    /** 外部目录包的定位器 */
    private static class FileLocator implements PackSource.Locator {
        private final String basePath;

        FileLocator(String basePath) {
            this.basePath = basePath;
        }

        @Override
        public InputStream open(String relativeFile) throws IOException {
            return Files.newInputStream(resolve(relativeFile));
        }

        @Override
        public boolean exists(String relativeFile) {
            return Files.isRegularFile(resolve(relativeFile));
        }

        @Override
        public List<String> list(String relativeDir) {
            Path dir = new File(basePath, trimSlash(relativeDir)).toPath();
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                List<String> names = new ArrayList<>();
                stream.filter(Files::isRegularFile)
                        .forEach(path -> names.add(path.getFileName().toString()));
                names.sort(Comparator.naturalOrder());
                return names;
            } catch (IOException e) {
                return List.of();
            }
        }

        private Path resolve(String relativeFile) {
            return new File(basePath, relativeFile).toPath();
        }
    }

    /** 目录名去掉结尾的 {@code /}，便于两种定位器统一拼路径 */
    private static String trimSlash(String relativeDir) {
        String dir = relativeDir == null ? "" : relativeDir.trim();
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir;
    }
}
