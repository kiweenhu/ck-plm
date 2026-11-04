/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.Manufacturer;
import cn.ck.plm.part.entity.PartAml;
import cn.ck.plm.part.entity.PartAvl;
import cn.ck.plm.part.entity.Supplier;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MPN 管理（制造商 / 供应商 / AML / AVL）数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>对应四张表：{@code ck_manufacturer}、{@code ck_supplier}、{@code ck_part_aml}、{@code ck_part_avl}。
 *
 * <h3>为什么四张表合用一个 Mapper</h3>
 * <p>本项目其它地方按"一表一 Mapper"组织（如 {@link PartAlternateLinkMapper}）。此处合并，
 * 是因为这四张表构成<b>一个不可分割的业务模块</b>：AML 离了制造商/供应商主数据没有意义，
 * AVL 离了 AML 也没有意义；读写它们的永远是同一个服务（{@code MpnService}）与同一个
 * 控制器（{@code /api/mpn/*}）。拆成四个 Mapper 只会制造四个必须同步演进的接口。
 * 若将来某张表被其它模块复用，再按使用面拆出不迟。
 *
 * <p>租户过滤不在此层书写：本项目有 {@code TenantStatementInterceptor} 自动为业务表
 * 注入 {@code tenant_oid = 当前租户}（这四张表均未列入共享名单）。
 */
public interface MpnMapper {

    // ==================== 制造商（ck_manufacturer）====================

    /** 列表；keyword 为空则返回全部（按 sort_order, code 排序） */
    List<Manufacturer> listManufacturers(@Param("keyword") String keyword);

    Manufacturer selectManufacturerByOid(@Param("oid") String oid);

    int insertManufacturer(Manufacturer manufacturer);

    int updateManufacturer(Manufacturer manufacturer);

    int deleteManufacturer(@Param("oid") String oid);

    /** 该制造商被多少 AML 行引用（删除前拦截：被引用的主数据不能删，只能停用） */
    int countAmlByManufacturer(@Param("manufacturerOid") String manufacturerOid);

    // ==================== 供应商（ck_supplier）====================

    List<Supplier> listSuppliers(@Param("keyword") String keyword);

    Supplier selectSupplierByOid(@Param("oid") String oid);

    int insertSupplier(Supplier supplier);

    int updateSupplier(Supplier supplier);

    int deleteSupplier(@Param("oid") String oid);

    /** 该供应商被多少 AVL 行引用（删除前拦截） */
    int countAvlBySupplier(@Param("supplierOid") String supplierOid);

    // ==================== AML（ck_part_aml）====================

    /** 某内部件的全部认可制造商料号 */
    List<PartAml> listAmlByPart(@Param("partOid") String partOid);

    PartAml selectAmlByOid(@Param("oid") String oid);

    int insertAml(PartAml aml);

    int updateAml(PartAml aml);

    int deleteAml(@Param("oid") String oid);

    /** 同一内部件 + 制造商 + 料号是否已存在（幂等键 uk_part_aml_mpn 的前置检查，给友好提示） */
    int countAmlByMpn(@Param("partOid") String partOid,
                      @Param("manufacturerOid") String manufacturerOid,
                      @Param("mpn") String mpn,
                      @Param("excludeOid") String excludeOid);

    // ==================== AVL（ck_part_avl）====================

    List<PartAvl> listAvlByAml(@Param("amlOid") String amlOid);

    /** 批量取多行 AML 的 AVL（用于按件查树时一次取回，避免 N+1） */
    List<PartAvl> listAvlByAmls(@Param("amlOids") List<String> amlOids);

    PartAvl selectAvlByOid(@Param("oid") String oid);

    int insertAvl(PartAvl avl);

    int updateAvl(PartAvl avl);

    int deleteAvl(@Param("oid") String oid);

    /** 同一 AML 行 + 供应商是否已存在（幂等键 uk_part_avl_pair 的前置检查） */
    int countAvlBySupplierPair(@Param("amlOid") String amlOid,
                               @Param("supplierOid") String supplierOid,
                               @Param("excludeOid") String excludeOid);

    // ==================== 汇总：补上 supplierRefs 那个洞 ====================

    /**
     * 某内部件的<b>去重供应商数</b> —— 即行业包属性声明里
     * {@code supplierCount.derivedBy = count(distinct supplierRefs)} 的口径：
     * 该件全部 AML 行下挂的全部 AVL 行的 supplier_oid 去重计数。
     *
     * <p>值为 1 即"单一来源器件"，对应检查规则 R-SH-11「单一来源器件风险提示」。
     */
    int countDistinctSuppliersByPart(@Param("partOid") String partOid);

    /** 该内部件有多少个 AML 行（汇总用） */
    int countAmlByPart(@Param("partOid") String partOid);

    // ==================== 内部件选择器 ====================

    /**
     * 按关键字搜索内部件（{@code ck_part}），供前端"选择内部件"用。
     * 只取 oid / code / name 三列 —— 选择器只需要认出是哪个件。
     */
    List<java.util.Map<String, Object>> searchParts(@Param("keyword") String keyword,
                                                    @Param("limit") int limit);
}
