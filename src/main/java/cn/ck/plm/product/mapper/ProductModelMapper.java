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

    /** 逻辑删除（置 delete_mark=true） */
    int softDeleteByOid(String oid);

    /** 从回收站恢复（delete_mark=false） */
    int restoreByOid(String oid);

    int deleteByOid(String oid);

    /** 查询回收站（delete_mark=true） */
    List<ProductModel> selectDeleted();

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
