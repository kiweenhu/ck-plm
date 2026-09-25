/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link SoftTypeInstanceService} 默认实现 —— 以 {@code root_type_code} 为唯一真相做路由。
 *
 * <p>路由链路见接口注释。本类只做「解析 + 委托」，不含任何宿主业务逻辑 ——
 * 业务逻辑全在宿主自己的能力策略里（{@link SoftTypeInstanceCapability} 的实现）。
 */
@Service
public class SoftTypeInstanceServiceImpl implements SoftTypeInstanceService {

    private static final Logger log = LoggerFactory.getLogger(SoftTypeInstanceServiceImpl.class);

    private final TypeDefinitionService typeDefinitionService;
    private final SoftTypeInstanceRegistry registry;

    public SoftTypeInstanceServiceImpl(TypeDefinitionService typeDefinitionService,
                                       SoftTypeInstanceRegistry registry) {
        this.typeDefinitionService = typeDefinitionService;
        this.registry = registry;
    }

    // ==================== 统一入口 ====================

    @Override
    public SoftTypeInstanceResult create(String typeDefinitionCode, Map<String, Object> payload) {
        return doCreate(null, typeDefinitionCode, payload);
    }

    @Override
    public Object get(String typeDefinitionCode, String oid, Map<String, Object> params) {
        return capabilityOfType(typeDefinitionCode).getInstance(requireOid(oid), body(params));
    }

    @Override
    public Object update(String typeDefinitionCode, String oid, Map<String, Object> body) {
        return capabilityOfType(typeDefinitionCode).updateInstance(requireOid(oid), body(body));
    }

    @Override
    public Object rename(String typeDefinitionCode, String oid, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("新名称不能为空");
        }
        return capabilityOfType(typeDefinitionCode).renameInstance(requireOid(oid), name.trim());
    }

    @Override
    public Object move(String typeDefinitionCode, String oid, Map<String, Object> body) {
        return capabilityOfType(typeDefinitionCode).moveInstance(requireOid(oid), body(body));
    }

    @Override
    public Object saveAs(String typeDefinitionCode, String oid, Map<String, Object> body) {
        return capabilityOfType(typeDefinitionCode).saveAsInstance(requireOid(oid), body(body));
    }

    @Override
    public void delete(String typeDefinitionCode, String oid) {
        capabilityOfType(typeDefinitionCode).deleteInstance(requireOid(oid));
    }

    @Override
    public void deleteLatestIteration(String typeDefinitionCode, String oid) {
        capabilityOfType(typeDefinitionCode).deleteLatestIterationInstance(requireOid(oid));
    }

    @Override
    public void newViewVersion(String typeDefinitionCode, String oid) {
        capabilityOfType(typeDefinitionCode).newViewVersionInstance(requireOid(oid));
    }

    @Override
    public List<Map<String, Object>> iterations(String typeDefinitionCode, String oid) {
        return capabilityOfType(typeDefinitionCode).listIterationsInstance(requireOid(oid));
    }

    @Override
    public void setLifecycleState(String typeDefinitionCode, String oid, String entityVersion,
                                  String targetStateCode) {
        capabilityOfType(typeDefinitionCode)
                .setLifecycleStateInstance(requireOid(oid), entityVersion, targetStateCode);
    }

    @Override
    public void resetLifecycleState(String typeDefinitionCode, String oid, String entityVersion) {
        capabilityOfType(typeDefinitionCode).resetLifecycleStateInstance(requireOid(oid), entityVersion);
    }

    // ==================== 实体原生端点 ====================

    @Override
    public SoftTypeInstanceResult createForHost(String expectedHost, String typeDefinitionCode,
                                                Map<String, Object> payload) {
        return doCreate(expectedHost, typeDefinitionCode, payload);
    }

    @Override
    public Object getForHost(String hostCode, String oid, Map<String, Object> params) {
        return capability(hostCode).getInstance(requireOid(oid), body(params));
    }

    @Override
    public Object updateForHost(String hostCode, String oid, Map<String, Object> body) {
        return capability(hostCode).updateInstance(requireOid(oid), body(body));
    }

    // ==================== 能力发现 ====================

    @Override
    public Set<String> supportedOperations(String typeDefinitionCode) {
        Set<SoftTypeInstanceCapability.Operation> ops =
                capabilityOfType(typeDefinitionCode).supportedOperations();
        Set<String> codes = new LinkedHashSet<>();
        for (SoftTypeInstanceCapability.Operation op : ops) {
            codes.add(op.code());
        }
        return codes;
    }

    @Override
    public String resolveHost(String typeDefinitionCode) {
        return hostOf(requireType(typeDefinitionCode));
    }

    @Override
    public Set<String> supportedHosts() {
        return registry.supportedHosts();
    }

    // ==================== 私有工具 ====================

    /**
     * 创建实例的统一实现。
     *
     * @param expectedHost 非空时校验目标宿主，确保实体原生端点不会被「借用」创建异构对象
     */
    private SoftTypeInstanceResult doCreate(String expectedHost, String typeDefinitionCode,
                                            Map<String, Object> payload) {
        TypeDefinition type = requireType(typeDefinitionCode);
        // 已禁用的类型不应可再实例化。
        // 注意：该校验只作用于「创建」——禁用意味着停止新建，不应连带屏蔽既有数据的读取。
        if (!type.isEnabled()) {
            throw new IllegalArgumentException("类型 " + type.getCode() + " 已禁用，无法创建实例");
        }
        String host = hostOf(type);
        if (expectedHost != null && !expectedHost.trim().isEmpty()
                && !expectedHost.trim().equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("类型 " + type.getCode() + " 的能力宿主是 " + host
                    + "，不能通过宿主为 " + expectedHost.trim().toUpperCase(Locale.ROOT) + " 的端点创建");
        }
        SoftTypeInstanceCapability capability = capability(host);
        Map<String, Object> body = payload != null ? payload : Collections.emptyMap();

        log.info("对象创建：type={} (kind={}) → host={}", type.getCode(), type.getTypeKind(), host);

        SoftTypeInstanceResult result = capability.createInstance(type, body);

        // 统一回填路由元信息，保证响应中的身份字段与本次请求一致
        result.setHostCode(host);
        result.setTypeDefinitionCode(type.getCode());
        result.setTypeKind(type.getTypeKind());
        return result;
    }

    /** 按类型编码解析能力策略（统一入口用） */
    private SoftTypeInstanceCapability capabilityOfType(String typeDefinitionCode) {
        TypeDefinition type = requireType(typeDefinitionCode);
        return capability(hostOf(type));
    }

    /** 按宿主 code 解析能力策略（实体原生端点用） */
    private SoftTypeInstanceCapability capability(String hostCode) {
        if (hostCode == null || hostCode.trim().isEmpty()) {
            throw new IllegalArgumentException("hostCode 不能为空");
        }
        String host = hostCode.trim().toUpperCase(Locale.ROOT);
        SoftTypeInstanceCapability capability = registry.of(host);
        if (capability == null) {
            throw new IllegalArgumentException("能力宿主 " + host
                    + " 尚未实现能力策略（已支持宿主: " + registry.supportedHosts() + "）");
        }
        return capability;
    }

    private TypeDefinition requireType(String typeDefinitionCode) {
        if (typeDefinitionCode == null || typeDefinitionCode.trim().isEmpty()) {
            throw new IllegalArgumentException("typeDefinitionCode 不能为空");
        }
        TypeDefinition type = typeDefinitionService.findByCode(typeDefinitionCode.trim());
        if (type == null) {
            throw new IllegalArgumentException("类型定义不存在: " + typeDefinitionCode);
        }
        return type;
    }

    /** 解析能力宿主（{@code root_type_code}；OOTB 未回填时退化为自身 code） */
    private String hostOf(TypeDefinition type) {
        if (type.isDomain()) {
            throw new IllegalArgumentException(
                    "类型 " + type.getCode() + " 是业务域锚点（DOMAIN），只是命名空间，不能作为实例类型");
        }
        String host = type.getRootTypeCode();
        if (host == null || host.trim().isEmpty()) {
            host = type.getCode();
        }
        if (!registry.supports(host)) {
            throw new IllegalArgumentException("类型 " + type.getCode() + " 的能力宿主 "
                    + host.trim().toUpperCase(Locale.ROOT)
                    + " 尚未实现能力策略（已支持宿主: " + registry.supportedHosts() + "）");
        }
        return host.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        return oid.trim();
    }

    private static Map<String, Object> body(Map<String, Object> body) {
        return body != null ? body : Collections.emptyMap();
    }
}
