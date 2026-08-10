/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.entity.BomLinks;

import java.util.List;

/**
 * BomLinks 业务服务接口。
 */
public interface BomLinksService {

    BomLinks create(BomLinks bomLinks);

    BomLinks update(BomLinks bomLinks);

    void deleteByOid(String oid);

    BomLinks getByOid(String oid);

    /** 获取某父部件迭代下的所有 BOM 行项（按 lineNumber 排序） */
    List<BomLinks> listByParentIterationOid(String parentIterationOid);

    /** 查询某个子部件被哪些父部件引用 */
    List<BomLinks> listByChildPartOid(String childPartOid);

    /** 查询某个子部件精确迭代被哪些 BOM 行引用 */
    List<BomLinks> listByChildIterationOid(String childIterationOid);

    /** 刷新非精确引用的解析缓存 */
    void refreshResolvedIteration(String oid, String resolvedIterationOid);

    List<BomLinks> listAll();
}
