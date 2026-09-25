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
 * 自动服务 {@code object.promote} —— 「升版（新建大版本）」。
 *
 * <p>复用宿主已有的"新建视图版本"能力（{@code newViewVersion}）：新大版本 = revision+1、
 * iteration=1，并继承当前版本的状态与生命周期模板绑定 —— 与用户在列表上点"升版"是同一条路径，
 * 不另写一份（两份实现必然分叉）。
 *
 * <p>逐个对象升版，任一失败整体回滚：一批对象升到一半，版本号会变得说不清。
 */
@Component
public class PromoteHandler implements PlmServiceHandler {

    private final SoftTypeInstanceService softTypeInstanceService;

    public PromoteHandler(SoftTypeInstanceService softTypeInstanceService) {
        this.softTypeInstanceService = softTypeInstanceService;
    }

    @Override
    public String serviceId() {
        return "object.promote";
    }

    @Override
    public void execute(PlmServiceContext context) {
        if (context.targets().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」要升版，但该流程没有关联任何业务实体");
        }
        for (PlmServiceTarget target : context.targets()) {
            try {
                softTypeInstanceService.newViewVersion(target.getTypeCode(), target.getEntityOid());
            } catch (RuntimeException e) {
                throw new PlmServiceException("节点「" + context.nodeName() + "」升版失败："
                        + target.label() + "：" + e.getMessage(), e);
            }
        }
    }
}
