/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.dto.BomSubstituteGroupRequest;
import cn.ck.plm.bom.dto.BomSubstituteGroupVO;

import java.util.List;

/**
 * 成组替代（组头 + 两侧成员）业务接口。
 *
 * <p>与局部替代（{@link BomSubstituteService}）的分工：
 * 局部替代挂在<b>一条 BOM 行</b>上、换的是<b>一颗料</b>；
 * 成组替代挂在<b>父件迭代</b>上、换的是<b>一组行</b> —— 组上的"整组替换"约束表示
 * 必须整组一起换，拆开换任何一个都不成立。
 */
public interface BomSubstituteGroupService {

    /** 某父件迭代下的全部成组替代组（含两侧成员） */
    List<BomSubstituteGroupVO> listByParentIteration(String parentIterationOid);

    /** 新建组（状态固定从 DRAFT 开始） */
    BomSubstituteGroupVO create(BomSubstituteGroupRequest request);

    /** 更新组（两侧成员整体替换；已批准的组一旦被改动退回 DRAFT） */
    BomSubstituteGroupVO update(String oid, BomSubstituteGroupRequest request);

    /** 删除组（成员随组级联删除） */
    void delete(String oid);

    /** 改组状态：DRAFT / APPROVED / OBSOLETE（批准前要求两侧成员都不为空） */
    BomSubstituteGroupVO changeStatus(String oid, String status);
}
