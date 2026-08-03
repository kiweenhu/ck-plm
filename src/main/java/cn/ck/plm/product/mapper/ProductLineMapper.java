/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.ProductLine;

import java.util.List;

/**
 * 产品线数据访问接口，定义数据库无关的持久化契约。
 */
public interface ProductLineMapper {

    int insert(ProductLine productLine);

    int update(ProductLine productLine);

    int deleteByOid(String oid);

    ProductLine selectByOid(String oid);

    ProductLine selectByCode(String code);

    List<ProductLine> selectAll();

    List<ProductLine> search(String keyword);

    int existsByCode(String code);

    /** 查询根节点（parent_oid IS NULL），按 code 排序 */
    List<ProductLine> selectRoots();

    /** 查询指定父节点的直接子节点 */
    List<ProductLine> selectByParentOid(String parentOid);

    /** 查询某节点下直接子节点的数量 */
    int countByParentOid(String parentOid);

    /** 批量统计：按 parent_oid 分组返回子节点数量 */
    java.util.List<java.util.Map<String, Object>> countChildrenGroupByParentOid();

    /** 批量统计：按 parent_oid 分组返回产品型号数量 */
    java.util.List<java.util.Map<String, Object>> countModelsGroupByProductLineOid();
}
