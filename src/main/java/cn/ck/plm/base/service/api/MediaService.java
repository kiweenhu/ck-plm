/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.Media;

import java.util.List;
import java.util.Map;

/**
 * 图片空间服务接口。
 */
public interface MediaService {

    Media create(Media media);

    Media update(Media media);

    boolean delete(String oid);

    Media findByOid(String oid);

    List<Media> findAll();

    List<Media> search(String keyword);

    /**
     * 批量检查媒体引用状态
     * @param oids 媒体 oid 列表
     * @return Map<oid, true表示已被引用>
     */
    Map<String, Boolean> checkUsage(List<String> oids);
}
