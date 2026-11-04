/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.api;

import cn.ck.plm.part.entity.Manufacturer;
import cn.ck.plm.part.entity.PartAml;
import cn.ck.plm.part.entity.PartAvl;
import cn.ck.plm.part.entity.Supplier;

import java.util.List;
import java.util.Map;

/**
 * MPN 管理服务（参考 Windchill 的 AML/AVL），定义数据库无关的业务契约。
 *
 * <h3>业务模型三层</h3>
 * <pre>
 * 内部件 ck_part
 *   └─ AML  ck_part_aml ：哪些制造商料号被认可（制造商 + MPN）
 *        └─ AVL  ck_part_avl ：每个料号能从哪些供应商拿货
 * 主数据：ck_manufacturer（制造商） / ck_supplier（供应商）
 * </pre>
 */
public interface MpnService {

    // ==================== 制造商 ====================

    List<Manufacturer> listManufacturers(String keyword);

    Manufacturer getManufacturer(String oid);

    Manufacturer createManufacturer(Manufacturer manufacturer);

    Manufacturer updateManufacturer(String oid, Manufacturer patch);

    /**
     * 删除制造商。
     * <p>已被 AML 行引用时**拒绝删除**并提示改用停用 —— 被引用的主数据消失会让历史 AML 变成悬空引用。
     */
    void deleteManufacturer(String oid);

    // ==================== 供应商 ====================

    List<Supplier> listSuppliers(String keyword);

    Supplier getSupplier(String oid);

    Supplier createSupplier(Supplier supplier);

    Supplier updateSupplier(String oid, Supplier patch);

    /** 删除供应商；已被 AVL 行引用时拒绝，提示改用停用（同制造商口径） */
    void deleteSupplier(String oid);

    // ==================== AML / AVL ====================

    /**
     * 某内部件的 AML 树：AML 行列表，每行内嵌它的 AVL 行（{@code avls} 键）。
     *
     * <p>只返回 oid 关联与两表自有字段 —— 制造商名 / 供应商名由前端用主数据列表自行 join
     * （两边的列表都在同一页面里，量小；不为此多做一次查询）。
     */
    List<Map<String, Object>> listAmlTree(String partOid);

    PartAml createAml(PartAml aml);

    PartAml updateAml(String oid, PartAml patch);

    void deleteAml(String oid);

    PartAvl createAvl(PartAvl avl);

    PartAvl updateAvl(String oid, PartAvl patch);

    void deleteAvl(String oid);

    // ==================== 汇总（补 supplierRefs 那个洞）====================

    /**
     * 某内部件的 MPN 汇总。
     *
     * <p>关键字段 {@code supplierCount} = 该件全部 AML 行下挂的全部 AVL 行的供应商去重数 ——
     * 正是行业包属性声明里 {@code supplierCount.derivedBy = count(distinct supplierRefs)}
     * 的口径；{@code singleSource = true} 即命中检查规则 R-SH-11「单一来源器件风险提示」。
     */
    Map<String, Object> summarize(String partOid);

    /** 按关键字搜索内部件（前端"选择内部件"用），只返回 oid / code / name */
    List<Map<String, Object>> searchParts(String keyword, Integer limit);
}
