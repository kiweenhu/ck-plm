/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.bom.mapper.BomLinksMapper;
import cn.ck.plm.part.dto.GenPartEligibility;
import cn.ck.plm.part.entity.GenPartThresholdConfig;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.mapper.PartMapper;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 通用件认定阈值判定（单一事实源）。
 *
 * <p>前端菜单与后端发起闸门都走这里，避免"入口看得见、提交又被拒"的口径漂移。
 * 计数口径：统计窗口内引用该零件的 BOM 行数，以及沿 where-used 链向上覆盖到的
 * 不同型号数（产品系列/型号容器里 type=PART 的顶层对象去重；本地数据里型号既可能在
 * PRODUCT_MODEL 容器，也可能作为顶层 PART 直接挂在 PRODUCT_LINE 容器）。
 */
@Service
public class GenPartEligibilityService {

    @Autowired
    private GenPartThresholdConfigService configService;
    @Autowired
    private BomLinksMapper bomLinksMapper;
    @Autowired
    private PartMapper partMapper;
    @Autowired
    private ProcessTemplateMapper processTemplateMapper;

    /** 判定某零件是否达到本租户配置的通用件认定阈值 */
    public GenPartEligibility check(String partOid) {
        GenPartThresholdConfig config = configService.get();
        GenPartEligibility result = new GenPartEligibility();
        int minUsage = positive(config.getMinUsageCount(), 5);
        int minModel = positive(config.getMinModelCount(), 3);
        int months = config.getStatWindowMonths() == null ? 12 : config.getStatWindowMonths();
        result.setMinUsageCount(minUsage);
        result.setMinModelCount(minModel);
        result.setStatWindowMonths(months);
        result.setEligible(true);

        // 统计关掉 = 不做阈值拦截（配置页的 "启用通用件认定统计" 就是总开关）
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            result.setReason("通用件认定统计未启用，不做阈值拦截");
            return result;
        }
        if (partOid == null || partOid.trim().isEmpty()) {
            result.setEligible(false);
            result.setReason("缺少零件标识，无法统计引用情况");
            return result;
        }
        Part part = partMapper.selectByOid(partOid);
        List<String> scopes = config.getScopeTypeCodes();
        if (scopes.isEmpty()) {
            scopes = java.util.List.of("STRUCTURAL");
        }
        if (part != null && !scopes.contains(part.getTypeDefinitionCode())) {
            result.setReason("零件类型不在通用件认定统计范围内（" + String.join(" / ", scopes) + "）");
            return result;
        }

        Map<String, Object> counts = bomLinksMapper.selectGenPartUsage(
                partOid, TenantContext.get(), months);
        int usageCount = intValue(counts == null ? null : counts.get("usageCount"));
        int modelCount = intValue(counts == null ? null : counts.get("modelCount"));
        result.setUsageCount(usageCount);
        result.setModelCount(modelCount);

        if (usageCount < minUsage || modelCount < minModel) {
            result.setEligible(false);
            String window = months > 0 ? "近 " + months + " 个月" : "全部历史";
            result.setReason("未达到通用件认定阈值：" + window + "内被 " + usageCount + " 条 BOM 行引用、"
                    + "覆盖 " + modelCount + " 个产品型号（要求至少 " + minUsage + " 条、"
                    + minModel + " 个型号），暂不能发起升级流程");
        }
        return result;
    }

    /** 当前流程 key 是否就是配置里绑定的通用件认定流程（不硬编码 key） */
    public boolean isConfiguredFlow(String processKey) {
        GenPartThresholdConfig config = configService.get();
        if (!Boolean.TRUE.equals(config.getEnabled())
                || config.getProcessTemplateOid() == null
                || config.getProcessTemplateOid().trim().isEmpty()
                || processKey == null || processKey.trim().isEmpty()) {
            return false;
        }
        ProcessTemplate template = processTemplateMapper.selectByOid(config.getProcessTemplateOid());
        return template != null && processKey.equals(template.getKey());
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}
