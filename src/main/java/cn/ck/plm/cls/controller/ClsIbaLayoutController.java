package cn.ck.plm.cls.controller;

import cn.ck.plm.cls.entity.ClsPageLayout;
import cn.ck.plm.cls.service.api.ClsPageLayoutService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分类 IBA 布局控制器。
 * <p>使用 ck_cls_page_layout 表，为分类节点的 IBA 属性集
 * 提供 create / update / detail 三个操作的表单布局管理。
 */
@RestController
@RequestMapping("/api/cls-iba-layouts")
public class ClsIbaLayoutController {

    private final ClsPageLayoutService clsPageLayoutService;

    public ClsIbaLayoutController(ClsPageLayoutService clsPageLayoutService) {
        this.clsPageLayoutService = clsPageLayoutService;
    }

    /** 获取分类 IBA 的某个操作布局 */
    @GetMapping
    public ApiResponse<ClsPageLayout> get(@RequestParam String clsOid,
                                           @RequestParam String operationCode) {
        ClsPageLayout layout = clsPageLayoutService.findByClsAndOperation(clsOid, operationCode);
        return ApiResponse.ok(layout);
    }

    /** 保存或更新布局 */
    @PostMapping
    public ApiResponse<ClsPageLayout> save(@RequestBody ClsPageLayout layout) {
        return ApiResponse.ok(clsPageLayoutService.saveOrUpdate(layout));
    }

    /** 获取分类 IBA 的所有操作摘要 */
    @GetMapping("/operations")
    public ApiResponse<List<Map<String, String>>> listOperations(@RequestParam String clsOid) {
        return ApiResponse.ok(clsPageLayoutService.getOperationSummary(clsOid));
    }

    /** 删除布局 */
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam String clsOid,
                                     @RequestParam String operationCode) {
        clsPageLayoutService.deleteByClsAndOperation(clsOid, operationCode);
        return ApiResponse.ok();
    }
}
