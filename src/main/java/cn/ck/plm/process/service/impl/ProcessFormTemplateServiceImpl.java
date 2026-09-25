/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.process.entity.ProcessFormTemplate;
import cn.ck.plm.process.mapper.ProcessFormTemplateMapper;
import cn.ck.plm.process.service.api.ProcessFormTemplateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ProcessFormTemplateService} 实现。
 *
 * <h3>两条保护线</h3>
 * <ol>
 *   <li><b>内置模板</b>：不能删，且 code / nodeTypes / component 三个契约字段不能被改 ——
 *       运行期就是按这三个字段派发渲染，改了会让在途实例取不到表单；</li>
 *   <li><b>平台级数据</b>：内置模板属平台租户，普通租户不可改（
 *       {@link TenantContext#requireEditPermission}）—— 表是 PLATFORM_SHARED，
 *       查询宽、写入必须由服务层把关。</li>
 * </ol>
 *
 * <p>校验失败一律抛 {@link IllegalArgumentException}（可读中文原因），由控制器折成
 * {@code ApiResponse.fail} 返回 —— 前端拦截器会把它当提示弹出来。
 */
@Service
public class ProcessFormTemplateServiceImpl implements ProcessFormTemplateService {

    private final ProcessFormTemplateMapper mapper;

    public ProcessFormTemplateServiceImpl(ProcessFormTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProcessFormTemplate> list() {
        return mapper.selectList();
    }

    @Override
    public List<ProcessFormTemplate> listForNodeType(String nodeType) {
        List<ProcessFormTemplate> result = new ArrayList<>();
        for (ProcessFormTemplate template : mapper.selectList()) {
            if (isEnabled(template) && template.appliesTo(nodeType)) {
                result.add(template);
            }
        }
        return result;
    }

    @Override
    public ProcessFormTemplate create(ProcessFormTemplate template) {
        requireCode(template.getCode());
        if (mapper.countByCode(template.getCode(), null) > 0) {
            throw new IllegalArgumentException("表单编码已存在：" + template.getCode());
        }
        // 新建的一律是自定义模板：内置模板只能由启动初始化器登记
        template.setBuiltin(false);
        template.setEnabled(template.getEnabled() == null || template.getEnabled());
        template.setSortOrder(template.getSortOrder() == null ? 100 : template.getSortOrder());
        template.setTenantOid(TenantContext.get());
        mapper.insert(template);
        return mapper.selectByOid(template.getOid());
    }

    @Override
    public ProcessFormTemplate update(ProcessFormTemplate patch) {
        ProcessFormTemplate existing = mapper.selectByOid(patch.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("表单不存在：" + patch.getOid());
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "表单");

        existing.setName(patch.getName() == null ? existing.getName() : patch.getName());
        existing.setDescription(patch.getDescription());
        if (patch.getEnabled() != null) {
            existing.setEnabled(patch.getEnabled());
        }
        if (patch.getSortOrder() != null) {
            existing.setSortOrder(patch.getSortOrder());
        }
        if (!isBuiltin(existing)) {
            // 自定义模板：适用节点类型可改（这就是"同一节点类型多种模板"的来源）
            if (patch.getNodeTypes() != null) {
                existing.setNodeTypes(patch.getNodeTypes());
            }
        }
        mapper.update(existing);
        return mapper.selectByOid(existing.getOid());
    }

    @Override
    public void delete(String oid) {
        ProcessFormTemplate existing = mapper.selectByOid(oid);
        if (existing == null) {
            return;
        }
        if (isBuiltin(existing)) {
            throw new IllegalArgumentException(
                    "「" + existing.getName() + "」是系统内置表单，不可删除（可改为停用）");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "表单");
        mapper.deleteByOid(oid);
    }

    // ==================== 内部工具 ====================

    private void requireCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("表单编码不能为空");
        }
        if (!code.trim().matches("[A-Za-z0-9_\\-]{2,64}")) {
            throw new IllegalArgumentException("表单编码只能是字母、数字、下划线、中划线（2–64 位）");
        }
    }

    private boolean isBuiltin(ProcessFormTemplate template) {
        return Boolean.TRUE.equals(template.getBuiltin());
    }

    private boolean isEnabled(ProcessFormTemplate template) {
        return template.getEnabled() == null || template.getEnabled();
    }
}
