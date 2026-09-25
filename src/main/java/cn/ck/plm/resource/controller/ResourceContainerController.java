/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业级资源库容器 REST 控制器：查询资源库根节点及其子资源库列表。
 */
@RestController
@RequestMapping("/api/resource-containers")
public class ResourceContainerController {

    private final ResourceContainerMapper mapper;

    public ResourceContainerController(ResourceContainerMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询企业资源库根节点 */
    @GetMapping("/root")
    public ApiResponse<ResourceContainer> root() {
        return ApiResponse.ok(mapper.selectRoot());
    }

    /** 查询资源库根节点下的全部资源子库 */
    @GetMapping("/children")
    public ApiResponse<List<ResourceContainer>> children() {
        ResourceContainer root = mapper.selectRoot();
        if (root == null) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(mapper.selectChildren(root.getOid()));
    }
}
