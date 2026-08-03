/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.Media;

import java.util.List;

/**
 * 图片空间数据访问接口。
 */
public interface MediaMapper {

    int insert(Media media);

    int update(Media media);

    int deleteByOid(String oid);

    Media selectByOid(String oid);

    List<Media> selectAll();

    List<Media> search(String keyword);

    /**
     * 批量检查媒体是否被引用（ck_product_line.thumbnail / ck_document_iteration.storage_path）
     * @param oids 媒体 oid 列表
     * @return 已被引用的媒体 oid 列表
     */
    List<String> findUsedOids(List<String> oids);
}
