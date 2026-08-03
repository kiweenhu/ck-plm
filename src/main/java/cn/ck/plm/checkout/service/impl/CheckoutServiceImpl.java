/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.checkout.dto.CheckoutVO;
import cn.ck.plm.checkout.service.api.CheckoutProvider;
import cn.ck.plm.checkout.service.api.CheckoutService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 检出聚合服务实现 —— 自动发现所有 CheckoutProvider 并聚合查询结果。
 *
 * <p>扩展方式：新增实体类型时，实现 CheckoutProvider 并声明为 Spring Bean 即可，
 * 无需修改此服务。
 */
@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final List<CheckoutProvider> providers;

    public CheckoutServiceImpl(List<CheckoutProvider> providers) {
        this.providers = providers;
    }

    @Override
    public List<CheckoutVO> findMyCheckouts(String userOid) {
        List<CheckoutVO> all = new ArrayList<>();
        for (CheckoutProvider provider : providers) {
            try {
                all.addAll(provider.findCheckedOutByUser(userOid));
            } catch (Exception e) {
                // 某个提供者失败不影响其他
            }
        }
        return all;
    }
}
