/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.service.MasterService;

/**
 * 主对象通用服务实现，参考 Windchill 的 Master 服务模式。
 *
 * <p>提供 {@link MasterEntity} 的子版本工厂与数据拷贝能力。
 * 子类（如 {@code DocumentServiceImpl}）只需重写模板方法
 * {@link #newIterationInstance()} 即可获得完整的子版本创建能力。
 *
 * <h3>模板方法</h3>
 * <pre>
 * MasterServiceImpl (通用实现)
 *   └── newIterationInstance()       ← 模板方法，子类必须重写
 *   └── createInitialIteration()     ← 基于模板方法，子类无需重写
 *   └── createDerivedIteration()     ← 基于模板方法 + copyFrom，子类无需重写
 *   └── updateFrom()                 ← 通用拷贝，子类可重写以复制专属字段
 * </pre>
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * MasterServiceImpl (this)   ←  相当于 WTDocumentService 的模板基类
 *   ↑ extends
 * DocumentServiceImpl        ←  等价于 生成 new DocumentIteration() 的实现
 * </pre>
 */
public abstract class MasterServiceImpl implements MasterService {

    /** 创建具体的 IterationEntity 子类实例——模板方法，子类必须重写 */
    protected abstract IterationEntity newIterationInstance();

    @Override
    public IterationEntity createInitialIteration(MasterEntity master) {
        IterationEntity iter = newIterationInstance();
        iter.setMasterOid(master.getOid());
        return iter;
    }

    @Override
    public IterationEntity createDerivedIteration(MasterEntity master, IterationEntity source) {
        IterationEntity iter = newIterationInstance();
        iter.setMasterOid(master.getOid());
        // 标记副本：设置 derivedFromOid 和 derivedAt，重置检出状态
        iter.setDerivedFromOid(source.getOid());
        iter.setDerivedAt(java.time.LocalDateTime.now());
        iter.setCheckedOut(false);
        iter.setCheckedOutBy(null);
        iter.setCheckedOutComment(null);
        return iter;
    }

    @Override
    public void updateFrom(MasterEntity target, MasterEntity source) {
        target.setName(source.getName());
        target.setNumber(source.getNumber());
        target.setDescription(source.getDescription());
    }
}
