/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 能力宿主策略注册表 —— 把 {@link SoftTypeInstanceCapability} 按 {@code hostCode} 建索引。
 *
 * <p>由 Spring 注入全部能力实现（新增宿主只需让宿主自己的 Service 实现类实现
 * {@code SoftTypeInstanceCapability}，本类无需改动），运行时按
 * {@code type_definition.root_type_code} 查找。
 *
 * <p><b>失败快于错误</b>（启动阶段即校验，而非等到调用时）：
 * <ol>
 *   <li>同一宿主被重复注册 → 抛异常，避免「两个策略争抢同一宿主」导致路由不确定；</li>
 *   <li>{@code supportedOperations()} 未包含必备能力（CREATE / READ / UPDATE）→ 抛异常，
 *       避免宿主声明为能力宿主却无法被统一入口完整使用。</li>
 * </ol>
 */
@Component
public class SoftTypeInstanceRegistry {

    /** 必备能力：任何能力宿主都必须支持 */
    private static final Set<SoftTypeInstanceCapability.Operation> REQUIRED =
            Set.of(SoftTypeInstanceCapability.Operation.CREATE,
                    SoftTypeInstanceCapability.Operation.READ,
                    SoftTypeInstanceCapability.Operation.UPDATE);

    private final Map<String, SoftTypeInstanceCapability> byHost;

    public SoftTypeInstanceRegistry(List<SoftTypeInstanceCapability> capabilities) {
        Map<String, SoftTypeInstanceCapability> map = new LinkedHashMap<>();
        for (SoftTypeInstanceCapability capability : capabilities) {
            String host = normalize(capability.hostCode());
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException("能力宿主策略 " + capability.getClass().getName()
                        + " 未声明 hostCode()");
            }
            SoftTypeInstanceCapability previous = map.put(host, capability);
            if (previous != null) {
                throw new IllegalStateException("能力宿主策略重复注册：宿主 " + host
                        + " 同时由 " + previous.getClass().getSimpleName()
                        + " 与 " + capability.getClass().getSimpleName() + " 实现");
            }
            Set<SoftTypeInstanceCapability.Operation> ops = capability.supportedOperations();
            if (ops == null || !ops.containsAll(REQUIRED)) {
                throw new IllegalStateException("能力宿主 " + host + " 的 supportedOperations() 必须包含必备能力 "
                        + REQUIRED + "，实际为 " + ops);
            }
        }
        this.byHost = Collections.unmodifiableMap(map);
    }

    /** 按宿主 code 查找能力策略，未注册返回 null（大小写不敏感） */
    public SoftTypeInstanceCapability of(String hostCode) {
        return hostCode == null ? null : byHost.get(normalize(hostCode));
    }

    /** 是否已支持该宿主 */
    public boolean supports(String hostCode) {
        return of(hostCode) != null;
    }

    /** 已注册的宿主集合（保持注册顺序） */
    public Set<String> supportedHosts() {
        return byHost.keySet();
    }

    private static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
