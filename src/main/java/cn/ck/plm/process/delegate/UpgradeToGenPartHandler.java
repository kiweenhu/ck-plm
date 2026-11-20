/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.part.service.api.PartService;
import org.springframework.stereotype.Component;

/**
 * 自动服务 {@code part.upgradeToGenPart} —— 「升级为通用件」。
 *
 * <p>通用件升级认定流程在会签通过、办理人确认之后调用本服务，把流程关联的零件
 * 从结构件软类型改为通用件软类型，并迁入当前租户的通用件资源库容器。
 * 真正的落库在 {@link PartService#upgradeToGenPart(String)}（同一事务，幂等），
 * 本处理器只负责作用对象定位与错误信息包装。
 *
 * <p>作用对象取 {@link PlmServiceContext#targets()}（流程实体关联表），
 * 不用 businessKey —— 后者只有变更单这类独立主表才有，零件走的是实体关联。
 */
@Component
public class UpgradeToGenPartHandler implements PlmServiceHandler {

    private final PartService partService;

    public UpgradeToGenPartHandler(PartService partService) {
        this.partService = partService;
    }

    @Override
    public String serviceId() {
        return "part.upgradeToGenPart";
    }

    @Override
    public String label() {
        return "升级为通用件";
    }

    @Override
    public void execute(PlmServiceContext context) {
        if (context.targets().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName()
                    + "」要把零件升级为通用件，但该流程没有关联任何业务实体，无处可改");
        }
        for (PlmServiceTarget target : context.targets()) {
            String root = target.getRootTypeCode();
            if (root != null && !root.isEmpty() && !"PART".equalsIgnoreCase(root)) {
                throw new PlmServiceException("节点「" + context.nodeName()
                        + "」只能升级零件（PART），当前对象是 " + target.label());
            }
            try {
                partService.upgradeToGenPart(target.getEntityOid());
            } catch (RuntimeException e) {
                throw new PlmServiceException("节点「" + context.nodeName() + "」升级为通用件失败："
                        + target.label() + "：" + e.getMessage(), e);
            }
        }
    }
}
