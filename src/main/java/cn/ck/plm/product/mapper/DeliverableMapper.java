/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Deliverable;

import java.util.List;

/**
 * 交付件实例（ck_deliverable）数据访问接口。
 *
 * <p>本表按「业务表」处理（与 ck_stage 一致）：{@code tenant_oid} 由租户拦截器注入，
 * 故本接口的语句<b>不显式书写租户条件</b> —— 与模板表（{@code ck_deliverable_template}）
 * 的写法正好相反，别抄错。
 */
public interface DeliverableMapper {

    int insert(Deliverable deliverable);

    Deliverable selectByOid(String oid);

    /** 某归属单元（系列 / 型号）的全部交付件 */
    List<Deliverable> selectByOwnerOid(String ownerOid);

    int countByOwnerOid(String ownerOid);

    int deleteByOwnerOid(String ownerOid);

    /**
     * 更新执行态（PENDING / IN_PROGRESS / DONE / NA）。
     *
     * <p>2026-10-11 新增：交付件状态不再只由快照时的 PENDING 决定 —— 由
     * {@code DeliverableStatusEvaluator} 依据「产出物关联 + 关联对象的最新迭代状态 +
     * 对象是否在流程中」重算后写回（判定规则见该类的类注释）。
     */
    int updateStatus(String oid, String status);

    /**
     * 裁剪：停用 / 恢复（{@code disabled} = "本项目不适用"）。
     *
     * <p>只影响该实例所在的归属单元（系列 / 型号），与模板层的"停用"是两回事
     * （后者决定"以后新建的项目要不要这条"）；不删条目、可随时恢复。
     */
    int updateDisabled(String oid, boolean disabled);

    /** 裁剪：改强制项（强制 ↔ 可选）—— 只影响本归属单元，模板与其它项目不动 */
    int updateMandatory(String oid, boolean mandatory);

    /**
     * 裁剪：改**责任人**（角色码，如 {@code PRODUCT_MANAGER}）——
     * 它同时是"关联交付项"权限的判据（谁负责谁关联，见 ProductTeamRoleResolver）。
     */
    int updateOwnerRole(String oid, String ownerRole);
}
