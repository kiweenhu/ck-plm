/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.api;

import cn.ck.plm.checkout.dto.CheckoutVO;

import java.util.List;

/**
 * 检出提供者 —— 每种实体类型实现此接口，统一处理检出查询和检出操作。
 *
 * <p>扩展方式：新增实体类型时，实现此接口并声明为 Spring Bean，
 * CheckoutService 和 CheckoutOperationServiceImpl 会自动发现并路由。
 */
public interface CheckoutProvider {

    /** 实体类型标识（如 DOCUMENT、PART） */
    String getEntityType();

    /** 实体类型中文名（如 文档、零部件） */
    String getEntityTypeName();

    /** 查询指定用户检出的所有该类型实体 */
    List<CheckoutVO> findCheckedOutByUser(String userOid);

    /** 执行检出操作 */
    void checkout(String entityOid, String comment, String user);

    /** 取消检出（撤销检出，不保留修改） */
    void undoCheckout(String entityOid, String user);
}
