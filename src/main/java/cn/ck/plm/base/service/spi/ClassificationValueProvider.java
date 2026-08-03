/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.spi;

import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLASSIFICATION 类型段值提供器 —— 根据所选分类节点，拼接从根到该节点的分类码。
 *
 * <p>使用方式：在编码规则的段定义中新增类型为 {@code CLASSIFICATION} 的段，
 * 并在 {@code config} 中指定 {@code classificationOid}：
 * <pre>{@code
 * segmentType = "CLASSIFICATION"
 * config = {"classificationOid": "xxx-yyy-zzz"}
 * }</pre>
 *
 * <p>生成值示例：若分类树为 电子产品(code=ELEC) → 手机(code=PHONE) → iPhone(code=IP)，
 * 选择 iPhone 节点，则生成值为 {@code ELECPHONEIP}。
 */
@Component
public class ClassificationValueProvider implements SegmentValueProvider {

    private static final Logger log = LoggerFactory.getLogger(ClassificationValueProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClassificationMapper classificationMapper;

    public ClassificationValueProvider(ClassificationMapper classificationMapper) {
        this.classificationMapper = classificationMapper;
    }

    @Override
    public boolean supports(String segmentType) {
        return "CLASSIFICATION".equalsIgnoreCase(segmentType);
    }

    @Override
    public String generate(NumberSegment segment, boolean increment) {
        String config = segment.getConfig();
        if (config == null || config.isBlank()) {
            log.warn("CLASSIFICATION 段未配置 classificationOid (segmentOid={})", segment.getOid());
            return "";
        }

        String classificationOid;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = MAPPER.readValue(config, Map.class);
            classificationOid = (String) configMap.get("classificationOid");
        } catch (Exception e) {
            log.warn("CLASSIFICATION 段 config 解析失败 (segmentOid={}): {}", segment.getOid(), e.getMessage());
            return "";
        }

        if (classificationOid == null || classificationOid.isBlank()) {
            log.warn("CLASSIFICATION 段 config 中未找到 classificationOid (segmentOid={})", segment.getOid());
            return "";
        }

        // 从根到目标节点收集所有分类码
        List<String> codes = collectAncestorCodes(classificationOid);
        if (codes.isEmpty()) {
            log.warn("未找到分类节点或其祖先: classificationOid={}", classificationOid);
            return "";
        }

        return String.join("", codes);
    }

    /**
     * 从当前节点逐级向上找到根节点，收集所有节点的 code，最后反转得到从根到叶的顺序。
     */
    private List<String> collectAncestorCodes(String oid) {
        List<String> codes = new ArrayList<>();
        String currentOid = oid;
        // 安全限制：最多遍历 50 层
        int maxDepth = 50;
        while (currentOid != null && maxDepth-- > 0) {
            Classification node = classificationMapper.selectByOid(currentOid);
            if (node == null) break;
            codes.add(node.getCode() != null ? node.getCode() : "");
            currentOid = node.getParentOid();
        }
        // 反转，使顺序为 根 → ... → 目标节点
        java.util.Collections.reverse(codes);
        return codes;
    }
}
