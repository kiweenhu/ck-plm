/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.service.api;

import cn.ck.plm.functional.entity.FunctionalIteration;

import java.util.List;

public interface FunctionalIterationService {

    FunctionalIteration create(FunctionalIteration iteration);

    FunctionalIteration update(FunctionalIteration iteration);

    void delete(String oid);

    FunctionalIteration findByOid(String oid);

    FunctionalIteration findLatestByMasterOid(String masterOid);

    List<FunctionalIteration> findByMasterOid(String masterOid);
}
