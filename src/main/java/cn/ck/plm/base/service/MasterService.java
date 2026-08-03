/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.MasterEntity;

/**
 * 主对象服务契约（MasterService），参考 Windchill MasterService 模式。
 *
 * <p>与实体解耦的独立服务层，提供主对象的创建与维护能力。
 * Entity 本身不实现本接口——调用方通过 Service 层操作实体。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * MasterService (this)          ←  wt.doc.WTDocumentService / wt.part.WTPartService
 *   ↑ implements
 * MasterServiceImpl             ←  通用实现，提供模板方法 newIterationInstance()
 *   ↑ extends
 * DocumentServiceImpl           ←  文档专属实现，重写工厂方法
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 贫血模型标准用法：Service + Entity 分离
 * DocumentServiceImpl docService = new DocumentServiceImpl();
 * Document doc = new Document();
 * doc.setName("BOM文档");
 * IterationEntity iter = docService.createInitialIteration(doc);
 * </pre>
 */
public interface MasterService {

    /**
     * 为首个主对象创建初始子版本（A.1）。
     *
     * @param master 主对象
     * @return 已关联 masterOid 的初始子版本
     */
    IterationEntity createInitialIteration(MasterEntity master);

    /**
     * 基于已有子版本创建派生副本。
     *
     * @param master 目标主对象
     * @param source 来源子版本
     * @return 已关联 masterOid 并标记副本的新子版本
     */
    IterationEntity createDerivedIteration(MasterEntity master, IterationEntity source);

    /**
     * 从源主对象拷贝核心属性（name / number / description）到目标。
     *
     * @param target 目标主对象
     * @param source 数据来源
     */
    void updateFrom(MasterEntity target, MasterEntity source);
}
