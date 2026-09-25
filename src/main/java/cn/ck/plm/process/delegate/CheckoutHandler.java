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
 * 自动服务 {@code object.checkout} —— 「检出对象」。
 *
 * <p>对流程关联的每个对象执行检出（按能力宿主分派 provider，见 {@code CheckoutOperationServiceImpl}）。
 *
 * <p><b>执行者是流程发起人</b>：检出会锁住对象（只有检出人能检入），所以"谁在办"必须落在具体人身上，
 * 不能是"系统"——否则这个对象谁也检不回来。发起人取流程变量 {@code initiator}（Flowable 的
 * {@code flowable:initiator} 已经写好了它）。
 */
@Component
public class CheckoutHandler implements PlmServiceHandler {

    /** 检出说明（可选参数，写进检出记录里） */
    private static final String PARAM_COMMENT = "comment";

    private final CheckoutOperationServiceImpl checkoutOperationService;

    public CheckoutHandler(CheckoutOperationServiceImpl checkoutOperationService) {
        this.checkoutOperationService = checkoutOperationService;
    }

    @Override
    public String serviceId() {
        return "object.checkout";
    }

    @Override
    public void execute(PlmServiceContext context) {
        String user = requireActor(context);
        String comment = context.param(PARAM_COMMENT);
        if (context.targets().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」要检出对象，但该流程没有关联任何业务实体");
        }
        for (PlmServiceTarget target : context.targets()) {
            try {
                checkoutOperationService.checkout(target.getRootTypeCode(), target.getEntityOid(), comment, user);
            } catch (RuntimeException e) {
                throw new PlmServiceException("节点「" + context.nodeName() + "」检出失败："
                        + target.label() + "：" + e.getMessage(), e);
            }
        }
    }

    /** 检出/检入必须落在具体人身上：拿不到发起人就明确失败，不要让对象被"无名者"锁住 */
    static String requireActor(PlmServiceContext context) {
        String user = context.actor();
        if (user == null || user.trim().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName()
                    + "」需要以某个人的身份执行，但流程里没有发起人信息（initiator 变量为空）");
        }
        return user.trim();
    }
}
