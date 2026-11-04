/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.revise.service.impl;

import cn.ck.plm.revise.service.api.ReviseProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 修订操作聚合服务 —— 自动发现所有 {@link ReviseProvider} 并按实体类型路由修订请求。
 *
 * <p>与 {@code CheckoutOperationServiceImpl} 同一套路由方式：Provider 由 Spring 注入，
 * 新增实体类型只需加一个实现类，本类无需改动。
 */
@Service
public class ReviseOperationServiceImpl {

    private final Map<String, ReviseProvider> providerMap;

    public ReviseOperationServiceImpl(List<ReviseProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(ReviseProvider::getEntityType, Function.identity(),
                        (existing, duplicate) -> existing));
    }

    /**
     * 按实体类型执行修订操作。
     */
    public void revise(String entityType, String entityOid, String user) {
        ReviseProvider provider = providerMap.get(entityType);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityType);
        }
        provider.revise(entityOid, user);
    }

    /** 已注册的实体类型（诊断 / 能力探询用） */
    public Collection<String> supportedEntityTypes() {
        return providerMap.keySet();
    }
}
