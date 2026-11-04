/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.controller;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业级资源库容器 REST 控制器：查询资源库根节点及其子资源库列表。
 *
 * <p>资源库**按租户隔离**（2026-10-11 起）：查询限当前登录租户自己那份；
 * 引导装包前本租户还没有资源库 → 根为 null / 子库为空列表（前端按空态处理）。
 */
@RestController
@RequestMapping("/api/resource-containers")
public class ResourceContainerController {

    private final ResourceContainerMapper mapper;

    public ResourceContainerController(ResourceContainerMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询本租户的企业资源库根节点 */
    @GetMapping("/root")
    public ApiResponse<ResourceContainer> root() {
        return ApiResponse.ok(mapper.selectRoot(TenantContext.get()));
    }

    /** 查询本租户资源库根节点下的全部资源子库 */
    @GetMapping("/children")
    public ApiResponse<List<ResourceContainer>> children() {
        String tenantOid = TenantContext.get();
        ResourceContainer root = mapper.selectRoot(tenantOid);
        if (root == null) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(mapper.selectChildren(root.getOid(), tenantOid));
    }
}
