/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.checkout.service.api.CheckoutProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检出操作聚合服务 —— 自动发现所有 CheckoutProvider 并按实体类型路由检出请求。
 */
@Service
public class CheckoutOperationServiceImpl {

    private final Map<String, CheckoutProvider> providerMap;

    public CheckoutOperationServiceImpl(List<CheckoutProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(CheckoutProvider::getEntityType, Function.identity(),
                        (existing, duplicate) -> existing));
    }

    /**
     * 按实体类型执行检出操作。
     */
    public void checkout(String entityType, String entityOid, String comment, String user) {
        CheckoutProvider provider = providerMap.get(entityType);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityType);
        }
        provider.checkout(entityOid, comment, user);
    }

    /**
     * 按实体类型执行取消检出操作。
     */
    public void undoCheckout(String entityType, String entityOid, String user) {
        CheckoutProvider provider = providerMap.get(entityType);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityType);
        }
        provider.undoCheckout(entityOid, user);
    }
}
