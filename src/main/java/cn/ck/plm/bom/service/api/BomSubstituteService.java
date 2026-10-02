/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.dto.BomSubstituteVO;
import cn.ck.plm.bom.entity.BomSubstituteLink;

import java.util.List;

/**
 * 局部替代（BOM 行替代）服务 —— 「某一 BOM 行上的子件，可以被哪些零件替换」。
 *
 * <p>与<b>全局替代</b>（{@code PartAlternate}：物料主数据级、任意 BOM 中均可引用）不同，
 * 局部替代只在<b>该 BOM 行</b>的上下文里成立 —— 同一颗料装在别处未必允许被同一个件替换。
 * 这也是「局部替代」这个名字的由来（Windchill 的 {@code WTPartSubstituteLink}）。
 *
 * <p><b>原子件由 BOM 行决定，不接受调用方传入</b>：源件就是该行的子件，
 * 让前端传就等于允许"替代关系挂在这行、源件却写另一个零件"的错配。
 */
public interface BomSubstituteService {

    /** 某 BOM 行的替代件清单（按优先级、创建时间排序） */
    List<BomSubstituteVO> listByBomLink(String bomLinkOid);

    /**
     * 设置替代件。
     *
     * <p>同一 BOM 行 + 同一替代件重复设置时<b>更新其参数</b>（类型/数量/单位/优先级/说明），
     * 不报"已存在"——「设置替代」重按一次就该是更新，而不是让用户先去删。
     *
     * @throws IllegalArgumentException BOM 行不存在、替代件不存在、或用该行子件替代它自己
     */
    BomSubstituteVO add(BomSubstituteLink link);

    /** 删除一条替代关系 */
    void delete(String oid);

    /** 清空某 BOM 行的全部替代（「取消替代」），返回删除条数 */
    int clearByBomLink(String bomLinkOid);
}
