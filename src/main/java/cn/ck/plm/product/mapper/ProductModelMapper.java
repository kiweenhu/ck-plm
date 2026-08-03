/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.ProductModel;

import java.util.List;

/**
 * 产品型号数据访问接口，定义数据库无关的持久化契约。
 */
public interface ProductModelMapper {

    int insert(ProductModel model);

    int update(ProductModel model);

    int deleteByOid(String oid);

    ProductModel selectByOid(String oid);

    ProductModel selectByCode(String code);

    List<ProductModel> selectAll();

    /** 按所属产品系列查询 */
    List<ProductModel> selectByProductLineOid(String productLineOid);

    List<ProductModel> search(String keyword);

    int existsByCode(String code);

    /** 批量统计：按 parent_oid 分组返回产品型号数量 */
    List<java.util.Map<String, Object>> countGroupByProductLineOid();
}
