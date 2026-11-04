/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * 已加载的包 —— 清单 + 能力开关 + 它的"资源定位器"。
 *
 * <p>清单（{@code pack.json}）里资源是以<b>相对路径</b>声明的（{@code softtypes.json}、
 * {@code processes/xxx.bpmn20.xml} …），而包可能来自 classpath（内置包）也可能来自外部目录
 * （实施顾问不改 jar 换包）。于是把"怎么找到这个包的文件"抽成一个 {@link Locator}：
 * Installer 只管说"我要 softtypes.json"，不用关心包在哪。
 *
 * <p>这样设计还有一个目的：<b>让 Installer 与包来源解耦</b> —— 未来从数据库或远端仓库装包时，
 * 只需换一个 Locator 实现，7 个 Installer 一行都不改。
 */
public class PackSource {

    private final PackManifest manifest;
    private final Map<String, Object> capabilities;
    private final Locator locator;

    public PackSource(PackManifest manifest, Map<String, Object> capabilities, Locator locator) {
        this.manifest = manifest;
        this.capabilities = capabilities == null ? Collections.emptyMap() : capabilities;
        this.locator = locator;
    }

    public PackManifest getManifest() { return manifest; }

    public Map<String, Object> getCapabilities() { return capabilities; }

    /** 包坐标 {@code code@version} */
    public String getId() { return manifest.getId(); }

    /** 打开包内某个资源文件（调用方负责关流） */
    public InputStream open(String relativeFile) throws IOException {
        return locator.open(relativeFile);
    }

    /** 资源文件是否存在（可选资源用它判断后跳过） */
    public boolean exists(String relativeFile) {
        return locator.exists(relativeFile);
    }

    /**
     * 列出包内某个目录下的文件名（已排序；目录不存在或不可列时返回空表）。
     *
     * <p>用于"一个资源 = 一个目录"的形态：如 {@code processes/} 下每个流程一对文件
     * （{@code <key>.dsl.json} + {@code <key>.bpmn20.xml}）—— 多个流程就是多个文件，
     * 不必挤在一个大 JSON 里，改动 diff 也看得清。
     */
    public java.util.List<String> list(String relativeDir) {
        return locator.list(relativeDir);
    }

    /**
     * 读取包内 JSON 文件为 Map（缺失或解析失败时返回空 Map，由调用方决定是否报错）。
     * 行业包里的绝大多数资源文件都是"一个对象带 items/add/groups 数组"的形状，
     * 引擎按需取子结构，故这里只做最粗粒度的解析。
     */
    public Map<String, Object> readJsonMap(ObjectMapper objectMapper, String relativeFile) throws IOException {
        if (!exists(relativeFile)) {
            return Collections.emptyMap();
        }
        try (InputStream in = open(relativeFile)) {
            return objectMapper.readValue(in, new TypeReference<Map<String, Object>>() { });
        }
    }

    @Override
    public String toString() {
        return "PackSource{" + getId() + ", capabilities=" + capabilities.size() + "}";
    }

    /**
     * 包内资源定位器 —— classpath 包按 {@code classpath:...} 前缀，外部目录包按文件路径。
     */
    public interface Locator {
        InputStream open(String relativeFile) throws IOException;
        boolean exists(String relativeFile);
        /** 列出目录下的文件名（已排序；目录不存在返回空表） */
        java.util.List<String> list(String relativeDir);
    }
}
