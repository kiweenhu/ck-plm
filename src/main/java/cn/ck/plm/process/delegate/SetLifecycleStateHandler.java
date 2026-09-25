/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import org.springframework.stereotype.Component;

/**
 * 自动服务 {@code object.setLifecycleState} —— 「设置状态」。
 *
 * <p>参数 {@code state} 是<b>目标状态 code</b>（如 IN_WORK / PUBLISHED），
 * 由设计器从生命周期状态清单里选出来（人不用记 code）。
 *
 * <p><b>作用对象是"这批对象"而不是"主对象"</b>：一次流程可以带一批对象走审批，
 * 状态自然该一起变 —— 只改主对象会让另外几条悄悄停在旧状态，
 * 而且审批记录看起来"都过了"，最难发现。
 *
 * <p>先逐个校验、有一个不合法就整体失败（迁移规则由宿主 + 模板判定，
 * 见 {@code LifecycleTemplateService#moveToState}）：要么全成，要么都不动，
 * 不留下"改了一半"的中间态。
 */
@Component
public class SetLifecycleStateHandler implements PlmServiceHandler {

    /** 目标状态参数名（与前端服务参数声明一致） */
    private static final String PARAM_STATE = "state";

    private final SoftTypeInstanceService softTypeInstanceService;

    public SetLifecycleStateHandler(SoftTypeInstanceService softTypeInstanceService) {
        this.softTypeInstanceService = softTypeInstanceService;
    }

    @Override
    public String serviceId() {
        return "object.setLifecycleState";
    }

    @Override
    public void execute(PlmServiceContext context) {
        String state = context.param(PARAM_STATE);
        if (state == null) {
            throw new PlmServiceException("「设置状态」节点「" + context.nodeName()
                    + "」未配置目标状态（参数 state），请在设计器里选一个状态后再部署");
        }
        if (context.targets().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName()
                    + "」要设置状态，但该流程没有关联任何业务实体（ck_process_entity_set 无记录），无处可设");
        }
        for (PlmServiceTarget target : context.targets()) {
            try {
                softTypeInstanceService.setLifecycleState(
                        target.getTypeCode(), target.getEntityOid(), target.getEntityVersion(), state);
            } catch (RuntimeException e) {
                throw new PlmServiceException("节点「" + context.nodeName() + "」设置状态失败："
                        + target.label() + " → " + state + "：" + e.getMessage(), e);
            }
        }
    }
}
