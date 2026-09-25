/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析自动服务节点的<b>作用对象</b>：这个流程实例关联了哪些业务实体。
 *
 * <p>唯一真相是 {@code ck_process_entity_set}（发起时逐条写入，一行一个对象、各带大版本）——
 * 本表就是为"一个实例带一批对象"而生的，所以服务节点也按它取作用对象，
 * 而不是只看某个"主对象"变量（那份信息在表里，变量只是它在流程内的投影）。
 *
 * <p>回退路径：早期实例（关联表还没有记录的老数据）用流程变量 {@code businessObjectSet.primary}
 * 兜底，让它们不至于因为"没记录"而彻底跑不动。
 */
@Component
public class PlmTargetResolver {

    /** 业务对象集合变量名（见 ProcessInstanceController#businessObjectSetOf） */
    private static final String VAR_BUSINESS_OBJECT_SET = "businessObjectSet";

    private final ProcessEntitySetService entitySetService;

    public PlmTargetResolver(ProcessEntitySetService entitySetService) {
        this.entitySetService = entitySetService;
    }

    public List<PlmServiceTarget> resolve(DelegateExecution execution) {
        List<PlmServiceTarget> targets = new ArrayList<>();
        List<ProcessEntitySet> rows = entitySetService.findByProcessInstanceId(execution.getProcessInstanceId());
        for (ProcessEntitySet row : rows != null ? rows : List.<ProcessEntitySet>of()) {
            if (isBlank(row.getEntityOid())) {
                continue;
            }
            targets.add(new PlmServiceTarget(row.getTypeCode(), row.getRootTypeCode(),
                    row.getEntityOid(), row.getEntityVersion(), row.getBusinessKey()));
        }
        if (!targets.isEmpty()) {
            return targets;
        }
        PlmServiceTarget legacy = fromVariable(execution);
        if (legacy != null) {
            targets.add(legacy);
        }
        return targets;
    }

    /** 老实例兜底：从 businessObjectSet.primary（或更早的平铺形态）取对象 */
    private PlmServiceTarget fromVariable(DelegateExecution execution) {
        Object raw = execution.getVariable(VAR_BUSINESS_OBJECT_SET);
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<?, ?> set = (Map<?, ?>) raw;
        Object primary = set.get("primary");
        Map<?, ?> node = (primary instanceof Map) ? (Map<?, ?>) primary : set;
        String entityOid = str(node.get("entityOid"));
        if (isBlank(entityOid)) {
            return null;
        }
        return new PlmServiceTarget(str(node.get("typeCode")), str(node.get("rootTypeCode")),
                entityOid, str(node.get("entityVersion")), str(node.get("businessKey")));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
