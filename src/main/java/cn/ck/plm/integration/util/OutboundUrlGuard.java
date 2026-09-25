/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.util;

import cn.ck.plm.integration.config.IntegrationProperties;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 出站地址守卫 —— 在真正发请求之前判定这个地址能不能打。
 *
 * <h3>拦什么</h3>
 * <ul>
 *   <li>非 http/https 的 scheme（{@code file:} / {@code ftp:} 之类不该出现在流程配置里）；</li>
 *   <li>解析到<b>私网</b>(10/8、172.16/12、192.168/16)、<b>回环</b>(127/8、::1)、
 *       <b>链路本地</b>(169.254/16 —— 云元数据服务就在这)、<b>组播/未指定</b>、IPv6 ULA(fc00::/7) 的主机。</li>
 * </ul>
 *
 * <h3>为什么要解析域名后再判</h3>
 * <p>只按字面量拦 IP 是拦不住的：{@code http://internal.corp.example.com} 同样可能指向 10.x。
 * 所以对域名做一次解析，<b>只要有任一解析结果落在禁区就拒绝</b> —— 攻击者无法靠
 * "多返回一条公网 A 记录"把私网地址夹带进来。
 *
 * <h3>白名单</h3>
 * <p>命中的主机直接放行（含私网）—— 这是"确实要调内网系统"的正常出口，
 * 由管理员在 {@code plm.integration.allowed-hosts} 逐条登记。
 *
 * <h3>已知残留风险（需要时再补）</h3>
 * <p>校验与连接之间存在 DNS 重绑定窗口（校验时解析到公网、连接时解析到内网）。
 * 彻底堵法是把解析结果固定下来、直连该 IP 并带 Host 头，属于后续加固项。
 */
public final class OutboundUrlGuard {

    private OutboundUrlGuard() { }

    /** 允许的 scheme：只放行 web 出站 */
    private static final List<String> ALLOWED_SCHEMES = List.of("http", "https");

    /**
     * 校验地址，不允许时抛 {@link IllegalArgumentException}（错误信息直接给设计者看，
     * 所以要说清"哪里不允许、怎么才能允许"）。
     */
    public static void assertAllowed(String url, IntegrationProperties properties) {
        String host;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("接口地址不是合法 URL：" + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("接口地址只支持 http/https，当前是「" + uri.getScheme() + "」：" + url);
        }
        host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("接口地址缺少主机名：" + url);
        }
        int port = uri.getPort();

        boolean whitelisted = isWhitelisted(host, port, properties.getAllowedHosts());
        if (properties.isAllowPrivateHosts()) {
            return;
        }
        if (whitelisted) {
            return;
        }
        if (isBlockedHost(host)) {
            throw new IllegalArgumentException("接口地址「" + host + "」指向内网/本机地址，已被出站策略拦截。"
                    + "如果是内部系统，请让管理员把它加进 plm.integration.allowed-hosts 白名单");
        }
    }

    /**
     * 主机是否落在禁区（域名会先解析）。
     *
     * <p>包可见是为了能单测：不必真起流程，直接喂主机名/IP 即可。
     */
    static boolean isBlockedHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // 解析不了就拒：连域名都解析不了，请求必然失败，早失败早报清楚
            throw new IllegalArgumentException("接口地址的主机名解析不了：" + host);
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                return true;
            }
        }
        return false;
    }

    /** 单个地址是否属于禁区 */
    static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // IPv6 唯一本地地址 fc00::/7（Java 的 isSiteLocalAddress 只管 fec0::/10 的老定义）
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    /**
     * 白名单命中判定：支持 {@code 主机}、{@code 主机:端口}、{@code *.后缀} 三种写法。
     *
     * <p>端口写法是为"同一台机器上只有某个端口可信"这类场景留的。
     */
    static boolean isWhitelisted(String host, int port, List<String> allowedHosts) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        String normalized = host.toLowerCase();
        for (String raw : allowedHosts) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim().toLowerCase();
            String entryHost = entry;
            int entryPort = -1;
            int colon = entry.lastIndexOf(':');
            // 排除 IPv6 字面量里的冒号：只有一个冒号时才算 host:port
            if (colon > 0 && entry.indexOf(':') == colon) {
                entryHost = entry.substring(0, colon);
                try {
                    entryPort = Integer.parseInt(entry.substring(colon + 1));
                } catch (NumberFormatException e) {
                    entryHost = entry;
                    entryPort = -1;
                }
            }
            if (entryPort >= 0 && entryPort != port) {
                continue;
            }
            if (entryHost.startsWith("*.")) {
                String suffix = entryHost.substring(1);   // ".example.com"
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                    return true;
                }
                continue;
            }
            if (normalized.equals(entryHost)) {
                return true;
            }
        }
        return false;
    }
}
