/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.View;

import java.util.List;

/**
 * 视图服务接口，定义视图的 CRUD 操作规范。
 *
 * <p>职责：
 * <ul>
 *   <li>创建新视图</li>
 *   <li>编辑已有视图（编码不可变更）</li>
 *   <li>删除视图（级联删除关联的切换规则）</li>
 *   <li>查阅视图（按编码查找、列表查询）</li>
 * </ul>
 */
public interface ViewService {

    /** 创建新视图 */
    View create(View view);

    /** 编辑已有视图，code 不可变更 */
    View update(View view);

    /** 删除视图（级联删除关联切换规则） */
    boolean delete(String code);

    /** 按编码查询 */
    View findByCode(String code);

    /** 查询所有已启用视图 */
    List<View> findAllEnabled();

    /** 查询所有视图 */
    List<View> findAll();

    /** 模糊搜索（code / name） */
    List<View> search(String keyword);

    /** 判断编码是否已存在 */
    boolean exists(String code);
}
