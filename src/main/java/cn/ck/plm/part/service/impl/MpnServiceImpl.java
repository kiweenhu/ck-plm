/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.impl;

import cn.ck.plm.part.entity.Manufacturer;
import cn.ck.plm.part.entity.PartAml;
import cn.ck.plm.part.entity.PartAvl;
import cn.ck.plm.part.entity.Supplier;
import cn.ck.plm.part.mapper.MpnMapper;
import cn.ck.plm.part.service.api.MpnService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MPN 管理服务实现。
 *
 * <p><b>审计字段口径</b>：{@code createdAt / updatedAt} 在写库前显式赋当前时间
 * （INSERT 语句显式传了这两列，不设会被写成 NULL、绕过表的 DEFAULT）；
 * {@code creator / updater} 不在此层猜测身份 —— 留给审计拦截器按当前登录用户补齐，
 * 猜一个"system"反而会污染审计。
 *
 * <p><b>租户归属</b>：{@code tenantOid} 由 {@code TenantStatementInterceptor} 在 INSERT 时
 * 依据上下文注入（本层不写）—— 与项目其它业务模块同口径。
 */
@Service
public class MpnServiceImpl implements MpnService {

    private final MpnMapper mapper;

    public MpnServiceImpl(MpnMapper mapper) {
        this.mapper = mapper;
    }

    // ==================== 制造商 ====================

    @Override
    public List<Manufacturer> listManufacturers(String keyword) {
        return mapper.listManufacturers(trimToNull(keyword));
    }

    @Override
    public Manufacturer getManufacturer(String oid) {
        return mapper.selectManufacturerByOid(oid);
    }

    @Override
    @Transactional
    public Manufacturer createManufacturer(Manufacturer manufacturer) {
        LocalDateTime now = LocalDateTime.now();
        manufacturer.setCreatedAt(now);
        manufacturer.setUpdatedAt(now);
        if (manufacturer.getDisabled() == null) {
            manufacturer.setDisabled(false);
        }
        if (manufacturer.getSortOrder() == null) {
            manufacturer.setSortOrder(0);
        }
        mapper.insertManufacturer(manufacturer);
        return manufacturer;
    }

    @Override
    @Transactional
    public Manufacturer updateManufacturer(String oid, Manufacturer patch) {
        Manufacturer existing = mapper.selectManufacturerByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("制造商不存在: " + oid);
        }
        patch.setOid(oid);
        patch.setUpdatedAt(LocalDateTime.now());
        mapper.updateManufacturer(patch);
        return patch;
    }

    @Override
    @Transactional
    public void deleteManufacturer(String oid) {
        int refs = mapper.countAmlByManufacturer(oid);
        if (refs > 0) {
            throw new IllegalStateException("该制造商已被 " + refs + " 个 AML 行引用，不能删除；"
                    + "若不再使用请改为「停用」（历史 AML 需要保留对它的引用）");
        }
        mapper.deleteManufacturer(oid);
    }

    // ==================== 供应商 ====================

    @Override
    public List<Supplier> listSuppliers(String keyword) {
        return mapper.listSuppliers(trimToNull(keyword));
    }

    @Override
    public Supplier getSupplier(String oid) {
        return mapper.selectSupplierByOid(oid);
    }

    @Override
    @Transactional
    public Supplier createSupplier(Supplier supplier) {
        LocalDateTime now = LocalDateTime.now();
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);
        if (supplier.getDisabled() == null) {
            supplier.setDisabled(false);
        }
        if (supplier.getSortOrder() == null) {
            supplier.setSortOrder(0);
        }
        mapper.insertSupplier(supplier);
        return supplier;
    }

    @Override
    @Transactional
    public Supplier updateSupplier(String oid, Supplier patch) {
        Supplier existing = mapper.selectSupplierByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("供应商不存在: " + oid);
        }
        patch.setOid(oid);
        patch.setUpdatedAt(LocalDateTime.now());
        mapper.updateSupplier(patch);
        return patch;
    }

    @Override
    @Transactional
    public void deleteSupplier(String oid) {
        int refs = mapper.countAvlBySupplier(oid);
        if (refs > 0) {
            throw new IllegalStateException("该供应商已被 " + refs + " 个 AVL 行引用，不能删除；"
                    + "若不再使用请改为「停用」（历史 AVL 需要保留对它的引用）");
        }
        mapper.deleteSupplier(oid);
    }

    // ==================== AML / AVL ====================

    @Override
    public List<Map<String, Object>> listAmlTree(String partOid) {
        List<PartAml> amls = mapper.listAmlByPart(partOid);
        List<Map<String, Object>> result = new ArrayList<>();
        if (amls.isEmpty()) {
            return result;
        }
        List<String> amlOids = amls.stream().map(PartAml::getOid).collect(Collectors.toList());
        // 一次取回全部 AVL，避免按行 N+1
        Map<String, List<PartAvl>> avlByAml = mapper.listAvlByAmls(amlOids).stream()
                .collect(Collectors.groupingBy(PartAvl::getAmlOid, LinkedHashMap::new, Collectors.toList()));
        for (PartAml aml : amls) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("aml", aml);
            node.put("avls", avlByAml.getOrDefault(aml.getOid(), List.of()));
            result.add(node);
        }
        return result;
    }

    @Override
    @Transactional
    public PartAml createAml(PartAml aml) {
        int dup = mapper.countAmlByMpn(aml.getPartOid(), aml.getManufacturerOid(), aml.getMpn(), null);
        if (dup > 0) {
            throw new IllegalStateException("该内部件下已存在同一个「制造商 + 料号」的 AML 行："
                    + aml.getMpn() + "（同一料号重复录入没有意义）");
        }
        LocalDateTime now = LocalDateTime.now();
        aml.setCreatedAt(now);
        aml.setUpdatedAt(now);
        if (aml.getApprovedStatus() == null || aml.getApprovedStatus().isEmpty()) {
            aml.setApprovedStatus("APPROVED");
        }
        if (aml.getSortOrder() == null) {
            aml.setSortOrder(0);
        }
        mapper.insertAml(aml);
        return aml;
    }

    @Override
    @Transactional
    public PartAml updateAml(String oid, PartAml patch) {
        PartAml existing = mapper.selectAmlByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("AML 行不存在: " + oid);
        }
        int dup = mapper.countAmlByMpn(patch.getPartOid(), patch.getManufacturerOid(), patch.getMpn(), oid);
        if (dup > 0) {
            throw new IllegalStateException("同一个「制造商 + 料号」的 AML 行已存在：" + patch.getMpn());
        }
        patch.setOid(oid);
        patch.setUpdatedAt(LocalDateTime.now());
        mapper.updateAml(patch);
        return patch;
    }

    @Override
    @Transactional
    public void deleteAml(String oid) {
        // 其下 AVL 由外键 ON DELETE CASCADE 一并删除（渠道随料号消失）
        mapper.deleteAml(oid);
    }

    @Override
    @Transactional
    public PartAvl createAvl(PartAvl avl) {
        int dup = mapper.countAvlBySupplierPair(avl.getAmlOid(), avl.getSupplierOid(), null);
        if (dup > 0) {
            throw new IllegalStateException("该 AML 行下已挂过这家供应商（同一渠道重复挂没有意义）");
        }
        LocalDateTime now = LocalDateTime.now();
        avl.setCreatedAt(now);
        avl.setUpdatedAt(now);
        if (avl.getPreferred() == null) {
            avl.setPreferred(false);
        }
        if (avl.getSortOrder() == null) {
            avl.setSortOrder(0);
        }
        mapper.insertAvl(avl);
        return avl;
    }

    @Override
    @Transactional
    public PartAvl updateAvl(String oid, PartAvl patch) {
        PartAvl existing = mapper.selectAvlByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("AVL 行不存在: " + oid);
        }
        int dup = mapper.countAvlBySupplierPair(patch.getAmlOid(), patch.getSupplierOid(), oid);
        if (dup > 0) {
            throw new IllegalStateException("该 AML 行下已挂过这家供应商");
        }
        patch.setOid(oid);
        patch.setUpdatedAt(LocalDateTime.now());
        mapper.updateAvl(patch);
        return patch;
    }

    @Override
    @Transactional
    public void deleteAvl(String oid) {
        mapper.deleteAvl(oid);
    }

    // ==================== 汇总 ====================

    @Override
    public Map<String, Object> summarize(String partOid) {
        int amlCount = mapper.countAmlByPart(partOid);
        int supplierCount = mapper.countDistinctSuppliersByPart(partOid);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("partOid", partOid);
        summary.put("amlCount", amlCount);
        summary.put("supplierCount", supplierCount);
        summary.put("singleSource", supplierCount == 1);
        summary.put("riskNote", supplierCount == 1
                ? "单一来源风险：该内部件全部认可料号合计只有 1 家供应商（检查规则 R-SH-11）"
                : null);
        summary.put("derivation",
                "supplierCount = count(distinct ck_part_avl.supplier_oid) —— 对应 electronics-core/attributes.json "
                        + "里 supplierCount 的 derivedBy=count(distinct supplierRefs)");
        return summary;
    }

    @Override
    public List<Map<String, Object>> searchParts(String keyword, Integer limit) {
        return mapper.searchParts(trimToNull(keyword), limit == null || limit <= 0 ? 50 : limit);
    }

    // ==================== 内部工具 ====================

    /** 关键字归一：空白串按"不筛"处理（前端清空搜索框时不必传 null） */
    private static String trimToNull(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
