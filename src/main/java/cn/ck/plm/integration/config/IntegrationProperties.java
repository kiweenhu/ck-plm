/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 出站集成配置（{@code plm.integration}）—— 流程节点调用外部系统时的安全与超时边界。
 *
 * <h3>为什么默认禁私网</h3>
 * <p>「REST 接口调用」意味着<b>设计器里填的地址会被服务器发请求</b>。不加限制时，
 * 能配流程的人就能让服务器去请求任意地址：探测内网端口、读云厂商元数据服务
 * （{@code 169.254.169.254} 上挂着实例凭据）、拿内网响应当"接口返回"。
 * 这不是设计者的恶意问题 —— 多数是"随手填了个能通的地址"，但后果一样。
 *
 * <p>所以默认<b>不放行</b>私网 / 回环 / 链路本地 / 组播地址；确实要调内网系统时，
 * 由管理员把域名加进 {@link #getAllowedHosts()}（见 {@code allowedHosts}），
 * 逐条登记、可审计。
 *
 * <pre>
 * plm:
 *   integration:
 *     # 放行私网地址的租户级开关（默认 false：内网地址一律拦）
 *     allow-private-hosts: false
 *     # 白名单：命中的主机跳过私网拦截（内网系统在这里逐条登记）
 *     allowed-hosts:
 *       - erp.internal.example.com
 *       - 10.20.30.40:8080
 *     # 出站超时（秒）：发布/回写是流程中的一步，不能把办理人卡住
 *     timeout-seconds: 10
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "plm.integration")
public class IntegrationProperties {

    /** 是否允许调用私网地址（默认 false；仅当整台服务器的出站都可信时才该打开） */
    private boolean allowPrivateHosts = false;

    /**
     * 主机白名单：命中的主机放行（即使解析到私网地址）。
     *
     * <p>支持三种写法：{@code 主机名}、{@code 主机名:端口}、{@code *.example.com}（后缀通配）。
     */
    private List<String> allowedHosts = new ArrayList<>();

    /** 出站超时秒数 */
    private int timeoutSeconds = 10;

    public boolean isAllowPrivateHosts() { return allowPrivateHosts; }
    public void setAllowPrivateHosts(boolean allowPrivateHosts) { this.allowPrivateHosts = allowPrivateHosts; }
    public List<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts != null ? allowedHosts : new ArrayList<>();
    }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
