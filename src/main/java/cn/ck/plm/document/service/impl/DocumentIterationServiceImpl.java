package cn.ck.plm.document.service.impl;

import cn.ck.plm.document.entity.DocumentIteration;
import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.service.impl.IterationServiceImpl;

public class DocumentIterationServiceImpl extends IterationServiceImpl {

    public DocumentIterationServiceImpl() {
        super();
    }

    @Override
    public void updateFrom(IterationEntity target, IterationEntity source) {
        super.updateFrom(target, source);
        if (target instanceof DocumentIteration && source instanceof DocumentIteration) {
            DocumentIteration partTarget = (DocumentIteration) target;
            DocumentIteration partSource = (DocumentIteration) source;
            partTarget.setCkfileOid(partSource.getCkfileOid());
        }
    }
}
