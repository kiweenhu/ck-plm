/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.checkout.service.impl.CheckoutOperationServiceImpl;
import org.springframework.stereotype.Component;

/**
 * 自动服务 {@code object.checkin} —— 「检入对象」。
 *
 * <p>执行者同样是流程发起人（{@code initiator}）。检入时宿主会校验
 * "只有检出人才能检入"（见 {@code PartCheckoutProvider#checkin}）——
 * 所以「检出」与「检入」两个自动服务节点应当服务于同一批人：
 * 中途换人（比如审批人变成了发起人之外的某位）会在这里以可读的原因失败，
 * 而不是悄悄把工作副本丢掉。
 */
@Component
public class CheckinHandler implements PlmServiceHandler {

    private final CheckoutOperationServiceImpl checkoutOperationService;

    public CheckinHandler(CheckoutOperationServiceImpl checkoutOperationService) {
        this.checkoutOperationService = checkoutOperationService;
    }

    @Override
    public String serviceId() {
        return "object.checkin";
    }

    @Override
    public void execute(PlmServiceContext context) {
        String user = CheckoutHandler.requireActor(context);
        if (context.targets().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」要检入对象，但该流程没有关联任何业务实体");
        }
        for (PlmServiceTarget target : context.targets()) {
            try {
                checkoutOperationService.checkin(target.getRootTypeCode(), target.getEntityOid(), user);
            } catch (RuntimeException e) {
                throw new PlmServiceException("节点「" + context.nodeName() + "」检入失败："
                        + target.label() + "：" + e.getMessage(), e);
            }
        }
    }
}
