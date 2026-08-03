/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.LifecycleTemplateIteration;

/**
 * 生命周期模板子版本服务实现，参考 Windchill 版本控制服务模式。
 *
 * <p>继承 {@link IterationServiceImpl} 的全部版本控制能力，
 * 重写 {@link #updateFrom(IterationEntity, IterationEntity)} 以同步拷贝专属字段。
 */
public class LifecycleTemplateIterationServiceImpl extends IterationServiceImpl {

    @Override
    public void updateFrom(IterationEntity target, IterationEntity source) {
        super.updateFrom(target, source);
        // LifecycleTemplateIteration 当前无额外专属字段，仅保留扩展点
    }
}
