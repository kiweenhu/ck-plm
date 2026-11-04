/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service;

import cn.ck.plm.document.entity.EngDocPartLink;

import java.util.List;

/**
 * 工程数据 ↔ 零部件构建规则服务（EPM Build Rule）。
 *
 * <p>写操作负责三类业务约束：
 * <ol>
 *   <li>同一零件版本对同一份工程数据只允许一条规则（表上有唯一索引兜底）；</li>
 *   <li>{@code OWNER} / {@code CONTRIBUTING_IMAGE} 每个零件版本至多一条
 *       （对齐 Windchill EPMBuildRule 基数约束）；</li>
 *   <li>{@code assocType} 只能是 OWNER / CONTENT / CONTRIBUTING_IMAGE / IMAGE。</li>
 * </ol>
 */
public interface EngDocPartLinkService {

    /** 某个零件版本上的全部构建规则 */
    List<EngDocPartLink> findByPartIteration(String partIterationOid);

    /** 某份工程数据（主对象）被哪些零件版本引用 */
    List<EngDocPartLink> findByEngDocument(String engDocumentOid);

    /** 某个工程数据版本被哪些零件版本引用 */
    List<EngDocPartLink> findByEngDocIteration(String engDocIterationOid);

    EngDocPartLink findById(String oid);

    EngDocPartLink create(EngDocPartLink link);

    EngDocPartLink update(EngDocPartLink link);

    void delete(String oid);
}
