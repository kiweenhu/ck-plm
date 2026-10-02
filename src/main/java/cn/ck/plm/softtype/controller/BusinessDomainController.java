/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.BusinessDomain;
import cn.ck.plm.softtype.mapper.BusinessDomainMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务域只读接口 —— 供「类型定义」页的<b>域查看视图</b>与对象上的<b>域标签</b>回显使用。
 *
 * <p>域是平台预置数据（{@code ck_business_domain} 为共享表），所以这里只有查询、没有增删改。
 * 类型与域的关联是 {@code TypeDefinition.domainOid}（软引用），类型树本身<b>不</b>按域分层：
 * 域在界面上表现为每个类型/对象上的一个标签。
 */
@RestController
@RequestMapping("/api/business-domains")
public class BusinessDomainController {

    @Autowired
    private BusinessDomainMapper mapper;

    /** 全部启用的业务域（按 sort_order），用于域标签与域视图分组 */
    @GetMapping
    public ApiResponse<List<BusinessDomain>> list() {
        try {
            return ApiResponse.ok(mapper.selectEnabled(null, null));
        } catch (Exception e) {
            return ApiResponse.fail(500, "读取业务域失败: " + e.getMessage());
        }
    }
}
