package cn.ck.plm.cls.controller;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.service.api.ClassificationService;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.softtype.entity.IBA;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classifications")
public class ClassificationController {

    private final ClassificationService classificationService;
    private final ClsIbaDataService clsIbaDataService;
    private final UserService userService;

    public ClassificationController(ClassificationService classificationService,
                                     ClsIbaDataService clsIbaDataService,
                                     UserService userService) {
        this.classificationService = classificationService;
        this.clsIbaDataService = clsIbaDataService;
        this.userService = userService;
    }

    private String getCurrentTenantOid() {
        String username = UserContext.get();
        if (username != null) {
            User user = userService.findByUsername(username);
            if (user != null && user.getTenantOid() != null) {
                return user.getTenantOid();
            }
        }
        return TenantContext.get();
    }

    // ==================== CRUD ====================

    @PostMapping
    public ApiResponse<Classification> create(@RequestBody Classification classification) {
        if (classification.getTenantOid() == null) {
            classification.setTenantOid(getCurrentTenantOid());
        }
        return ApiResponse.ok(classificationService.create(classification));
    }

    @PutMapping("/{oid}")
    public ApiResponse<Classification> update(@PathVariable String oid, @RequestBody Classification classification) {
        classification.setOid(oid);
        return ApiResponse.ok(classificationService.update(classification));
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        classificationService.delete(oid);
        return ApiResponse.ok();
    }

    @GetMapping("/{oid}")
    public ApiResponse<Classification> getByOid(@PathVariable String oid) {
        return ApiResponse.ok(classificationService.findByOid(oid));
    }

    @GetMapping("/by-identifier/{identifier}")
    public ApiResponse<Classification> getByIdentifier(@PathVariable String identifier) {
        return ApiResponse.ok(classificationService.findByIdentifier(identifier));
    }

    @GetMapping
    public ApiResponse<List<Classification>> list() {
        return ApiResponse.ok(classificationService.findAll());
    }

    @GetMapping("/search")
    public ApiResponse<List<Classification>> search(@RequestParam String keyword) {
        return ApiResponse.ok(classificationService.search(keyword));
    }

    @GetMapping("/roots")
    public ApiResponse<List<Classification>> roots() {
        return ApiResponse.ok(classificationService.findRoots());
    }

    @GetMapping("/children/{parentOid}")
    public ApiResponse<List<Classification>> children(@PathVariable String parentOid) {
        return ApiResponse.ok(classificationService.findChildren(parentOid));
    }

    @GetMapping("/tree")
    public ApiResponse<List<Classification>> tree() {
        return ApiResponse.ok(classificationService.findTree());
    }

    // ==================== 分类-IBA 关联 ====================
    // 注意：不能使用 /{oid}/ibas 因为会与 /{oid} 冲突
    // 使用 /cls-iba 作为独立前缀避免路径变量歧义

    /** 查询分类关联的 IBA 列表 */
    @GetMapping("/cls-iba/list/{classificationOid}")
    public ApiResponse<List<ClassificationIBA>> listIBAs(@PathVariable String classificationOid) {
        return ApiResponse.ok(classificationService.findIBAsByClassificationOid(classificationOid));
    }

    /** 查询分类未分配的 IBA */
    @GetMapping("/cls-iba/unassigned/{classificationOid}")
    public ApiResponse<List<IBA>> listUnassignedIBAs(@PathVariable String classificationOid,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(classificationService.findUnassignedIBAs(classificationOid, keyword));
    }

    /** 为分类分配 IBA */
    @PostMapping("/cls-iba/assign/{classificationOid}")
    public ApiResponse<ClassificationIBA> assignIba(@PathVariable String classificationOid,
                                                     @RequestBody ClassificationIBA mapping) {
        mapping.setClassificationOid(classificationOid);
        if (mapping.getTenantOid() == null) {
            mapping.setTenantOid(getCurrentTenantOid());
        }
        return ApiResponse.ok(classificationService.assignIba(mapping));
    }

    /** 批量为分类分配 IBA */
    @PostMapping("/cls-iba/batch/{classificationOid}")
    public ApiResponse<List<ClassificationIBA>> batchAssignIbas(@PathVariable String classificationOid,
                                                                  @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ibaOids = (List<String>) body.get("ibaOids");
        if (ibaOids == null || ibaOids.isEmpty()) {
            return ApiResponse.fail(400, "ibaOids 不能为空");
        }
        return ApiResponse.ok(classificationService.batchAssignIbas(classificationOid, ibaOids));
    }

    /** 更新 IBA 映射 */
    @PutMapping("/cls-iba/{mappingOid}")
    public ApiResponse<ClassificationIBA> updateIBAMapping(@PathVariable String mappingOid,
                                                            @RequestBody ClassificationIBA mapping) {
        mapping.setOid(mappingOid);
        return ApiResponse.ok(classificationService.updateIBAMapping(mapping));
    }

    /** 移除 IBA 关联 */
    @DeleteMapping("/cls-iba/{mappingOid}")
    public ApiResponse<Void> removeIBAMapping(@PathVariable String mappingOid) {
        classificationService.removeIBAMapping(mappingOid);
        return ApiResponse.ok();
    }

    // ==================== 分类 IBA 数据存取 ====================

    /** 获取分类的 IBA 属性值 */
    @GetMapping("/{oid}/iba-values")
    public ApiResponse<Map<String, Object>> getIBAValues(@PathVariable String oid) {
        Map<String, Object> values = clsIbaDataService.getValues(oid);
        return ApiResponse.ok(values);
    }

    /** 保存分类的 IBA 属性值（全量替换） */
    @PostMapping("/{oid}/iba-values")
    public ApiResponse<Void> saveIBAValues(@PathVariable String oid,
                                           @RequestBody Map<String, Object> values) {
        clsIbaDataService.saveValues(oid, values);
        return ApiResponse.ok();
    }

    /** 合并保存分类的 IBA 属性值（仅更新提交的字段） */
    @PutMapping("/{oid}/iba-values")
    public ApiResponse<Void> mergeIBAValues(@PathVariable String oid,
                                            @RequestBody Map<String, Object> values) {
        clsIbaDataService.mergeValues(oid, values);
        return ApiResponse.ok();
    }
}
