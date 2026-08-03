package cn.ck.plm.part.service.impl;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.service.impl.IterationServiceImpl;
import cn.ck.plm.part.entity.PartIteration;

public class PartIterationServiceImpl extends IterationServiceImpl {

    public PartIterationServiceImpl() {
        super();
    }

    @Override
    public void updateFrom(IterationEntity target, IterationEntity source) {
        super.updateFrom(target, source);
        if (target instanceof PartIteration && source instanceof PartIteration) {
            PartIteration partTarget = (PartIteration) target;
            PartIteration partSource = (PartIteration) source;
            partTarget.setUnit(partSource.getUnit());
            partTarget.setSource(partSource.getSource());
        }
    }
}
