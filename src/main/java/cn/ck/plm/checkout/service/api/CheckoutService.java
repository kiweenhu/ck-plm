/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.api;

import cn.ck.plm.checkout.dto.CheckoutVO;

import java.util.List;

/**
 * 检出聚合服务契约。
 */
public interface CheckoutService {

    /** 查询指定用户所有检出对象（跨实体类型） */
    List<CheckoutVO> findMyCheckouts(String userOid);
}
