/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.GateTemplate;
import cn.ck.plm.base.entity.ReviewTemplate;
import cn.ck.plm.base.mapper.GateTemplateMapper;
import cn.ck.plm.base.mapper.ReviewTemplateMapper;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 技术评审「直接挂阶段」（2026-10-29 扁平化）的安装口径。
 *
 * <p>守三件事：
 * <ol>
 *   <li>技术评审清单的 8 条全部落 {@code ck_review_template}，<b>归属是 stage_key 而不是 gate</b>；</li>
 *   <li>阶段分布正确（开发阶段三场会：原理图 / 结构 / 软件）；</li>
 *   <li>决策门那份仍然只装门、不产评审 —— 门与评审现在是两件事，不该再纠缠在一起。</li>
 * </ol>
 */
class TechnicalReviewInstallTest {

    private static final Set<String> PACK_STAGES = Set.of(
            "IPD_CONCEPT", "IPD_PLAN", "IPD_DEVELOP", "IPD_VERIFY", "IPD_RELEASE", "IPD_LIFECYCLE");

    private ObjectMapper objectMapper;
    private IndustryPackLoader loader;
    private ReviewTemplateMapper reviewMapper;
    private GateTemplateMapper gateMapper;
    private final List<ReviewTemplate> insertedReviews = new ArrayList<>();
    private final List<GateTemplate> insertedGates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        loader = new IndustryPackLoader(objectMapper, "");
        reviewMapper = mock(ReviewTemplateMapper.class);
        gateMapper = mock(GateTemplateMapper.class);
        insertedReviews.clear();
        insertedGates.clear();
        when(reviewMapper.existsByCode(anyString(), anyString(), anyString())).thenReturn(0);
        when(gateMapper.existsByCode(anyString(), anyString(), anyString())).thenReturn(0);
        when(reviewMapper.insert(any(ReviewTemplate.class))).thenAnswer(inv -> {
            insertedReviews.add(inv.getArgument(0));
            return 1;
        });
        when(gateMapper.insert(any(GateTemplate.class))).thenAnswer(inv -> {
            insertedGates.add(inv.getArgument(0));
            return 1;
        });
    }

    @Test
    @DisplayName("技术评审：8 条全部挂阶段，gate 为空")
    void technicalReviewsHangOnStage() {
        List<InstallResult> results = installReviews(false);

        assertEquals(8, insertedReviews.size(), "技术评审应有 8 份");
        assertTrue(InstallResult.failures(results).isEmpty(), "不应有失败项: " + InstallResult.failures(results));
        for (ReviewTemplate review : insertedReviews) {
            assertNotNull(review.getStageKey(), "每份评审都必须有归属阶段: " + review.getCode());
            assertTrue(PACK_STAGES.contains(review.getStageKey()),
                    "阶段码必须来自 stages.json: " + review.getCode() + " → " + review.getStageKey());
            assertNull(review.getGate(), "扁平化后不应再挂门: " + review.getCode());
            assertNotNull(review.getProcessKey(), "评审应带流程 key: " + review.getCode());
        }
        // 开发阶段一个阶段三场会；历史码 TR4_HW_SCHEMATIC 保住（deliverables.json 的 REVIEW_PASSED 引用它）
        assertEquals(3, insertedReviews.stream().filter(r -> "IPD_DEVELOP".equals(r.getStageKey())).count());
        assertTrue(insertedReviews.stream().anyMatch(r -> "TR4_HW_SCHEMATIC".equals(r.getCode())
                && "IPD_DEVELOP".equals(r.getStageKey())));
        // 同一阶段内的顺序固定，界面上不会每次刷新都换位置
        assertTrue(insertedReviews.stream().filter(r -> "IPD_DEVELOP".equals(r.getStageKey()))
                .allMatch(r -> r.getSortOrder() != null && r.getSortOrder() >= 1 && r.getSortOrder() <= 3));
    }

    @Test
    @DisplayName("决策门：4 道门，不产评审")
    void decisionGatesProduceNoReviews() {
        List<InstallResult> results = installGates(false);

        assertEquals(4, insertedGates.size(), "决策门应有 4 道");
        assertTrue(insertedReviews.isEmpty(), "决策门不该产出评审（评审已挂阶段）");
        assertTrue(InstallResult.failures(results).isEmpty());
        assertTrue(insertedGates.stream().allMatch(g -> "DECISION_REVIEW".equals(g.getType())));
    }

    @Test
    @DisplayName("预演：一行不写，但要把 8 份评审都报出来")
    void dryRunWritesNothing() {
        List<InstallResult> results = installReviews(true);

        assertTrue(insertedReviews.isEmpty(), "预演不写评审");
        assertEquals(8, results.size(), "预演应逐条报出 8 份评审");
        assertTrue(results.stream().allMatch(r -> r.getAction() == InstallResult.Action.INSERT));
    }

    // ==================== 辅助 ====================

    private List<InstallResult> installReviews(boolean dryRun) {
        PackSource pack = loadPack();
        PackManifest.Resource resource = resource(pack, "technical-reviews.json");
        assertEquals("REVIEW", resource.getType(), "技术评审必须以 type=REVIEW 声明");
        ReviewTemplateInstaller installer = new ReviewTemplateInstaller(reviewMapper, gateMapper, loader);
        return installer.install(new InstallContext(pack, "tenant-under-test", dryRun, Map.of(), objectMapper),
                resource);
    }

    private List<InstallResult> installGates(boolean dryRun) {
        PackSource pack = loadPack();
        PackManifest.Resource resource = resource(pack, "decision-gates.json");
        assertEquals("GATE", resource.getType(), "决策门必须以 type=GATE 声明");
        GateTemplateInstaller installer = new GateTemplateInstaller(gateMapper, loader);
        return installer.install(new InstallContext(pack, "tenant-under-test", dryRun, Map.of(), objectMapper),
                resource);
    }

    private PackSource loadPack() {
        PackSource pack = loader.find("smart-hardware-ipd@1.0.0");
        assertNotNull(pack, "内置包应能从 classpath 加载（smart-hardware-ipd@1.0.0）");
        return pack;
    }

    private PackManifest.Resource resource(PackSource pack, String file) {
        return pack.getManifest().getResources().stream()
                .filter(r -> file.equals(r.getFile()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("pack.json 未声明资源文件: " + file));
    }
}
