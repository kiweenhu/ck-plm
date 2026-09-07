/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper;

import cn.ck.plm.resource.entity.ElectronicComponent;

import java.util.List;

/**
 * 电子元器件数据访问接口，对应表 {@code ck_electronic_component}。
 */
public interface ElectronicComponentMapper {

    int insert(ElectronicComponent component);

    int update(ElectronicComponent component);

    int deleteByOid(String oid);

    ElectronicComponent selectByOid(String oid);

    /** 按分类 + 关键字查询（categoryOid 可空 = 全部分类；keyword 模糊匹配编码/名称/型号/厂商） */
    List<ElectronicComponent> selectByCondition(String categoryOid, String keyword);

    /** 编码是否已存在 */
    int existsByCode(String code, String excludeOid);

    /** 全部元器件数量 */
    long countAll();

    /** 元器件总库存数量 */
    long sumStockQty();
}
