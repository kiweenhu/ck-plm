/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper;

import cn.ck.plm.ecad.entity.EcadDesignInstance;

import java.util.List;

/**
 * 设计实例（{@code ck_design_instance}）数据访问接口。
 *
 * <p>BOM 同步按"整组替换"写：先删该设计的全部实例，再插入本次上传的行。
 */
public interface EcadDesignInstanceMapper {

    /** 新增实例 */
    int insert(EcadDesignInstance instance);

    /** 按设计删除全部实例 */
    int deleteByDesign(String designOid, String designType);

    /** 按设计查询全部实例（按位号排序） */
    List<EcadDesignInstance> selectByDesign(String designOid, String designType);

    /** 按编号精确查找部件 oid（BOM 匹配用；查不到返回 null） */
    String selectPartOidByNumber(String number);
}
