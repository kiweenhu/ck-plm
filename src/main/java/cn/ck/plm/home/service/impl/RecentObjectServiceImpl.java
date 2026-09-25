/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.home.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.home.dto.RecentObjectVO;
import cn.ck.plm.home.mapper.RecentObjectMapper;
import cn.ck.plm.home.service.api.RecentObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「我最近创建/修改过的对象」服务实现。
 *
 * <h3>为什么把类型清单写在这里</h3>
 * <p>它必须与检出模块（{@code CheckoutProvider}）的"什么是业务对象"保持一致 ——
 * 目前是 部件 / 文档 / 工程数据 三类。新增第四类对象时，这两处都要加
 * （已在类注释里标明，避免以后只看一处）。
 */
@Service
public class RecentObjectServiceImpl implements RecentObjectService {

    private static final Logger log = LoggerFactory.getLogger(RecentObjectServiceImpl.class);

    /** 窗口上限：再长就不是"最近"了，且全表扫的时间成本随天数线性上涨 */
    private static final int MAX_DAYS = 90;
    private static final int MAX_LIMIT = 50;

    /** 对象类型 → 主表 / 版本表 / 跳转路径 */
    private record ObjectType(String code, String name, String masterTable, String iterTable, String linkPath) {}

    private static final List<ObjectType> OBJECT_TYPES = List.of(
            new ObjectType("PART", "部件", "ck_part", "ck_part_iteration", "/part"),
            new ObjectType("DOCUMENT", "文档", "ck_document", "ck_document_iteration", "/resource"),
            new ObjectType("ENG_DOCUMENT", "工程数据", "ck_eng_document", "ck_eng_document_iteration", "/resource")
    );

    private final RecentObjectMapper mapper;

    public RecentObjectServiceImpl(RecentObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RecentObjectVO> findMyRecentObjects(int days, int limit) {
        String user = UserContext.get();
        String tenant = TenantContext.get();
        if (user == null || tenant == null) {
            return List.of();
        }
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        LocalDateTime since = LocalDateTime.now().minusDays(window);

        List<RecentObjectVO> all = new ArrayList<>();
        for (ObjectType type : OBJECT_TYPES) {
            try {
                all.addAll(mapper.selectRecentlyTouched(type.code(), type.name(),
                        type.masterTable(), type.iterTable(), type.linkPath(),
                        tenant, user, since, size));
            } catch (Exception e) {
                // 单个类型失败不影响其余类型（与检出聚合同一策略）
                log.warn("查询最近对象失败: type={} user={} error={}", type.code(), user, e.getMessage());
            }
        }

        for (RecentObjectVO vo : all) {
            // 查询已按"我"过滤过，所以时间落在窗口内即代表那次动作是我做的
            boolean created = vo.getCreatedAt() != null && !vo.getCreatedAt().isBefore(since);
            // 关键：创建时 created_at 与 updated_at 是同一个时刻，光看"updated_at 在窗口内"会把
            // 新建的对象误判成"修改"。修改必须是<b>晚于</b>创建的那一刻（同一毫秒的相等视为只创建）。
            boolean updated = vo.getUpdatedAt() != null && vo.getCreatedAt() != null
                    && vo.getUpdatedAt().isAfter(vo.getCreatedAt())
                    && !vo.getUpdatedAt().isBefore(since);
            vo.setTouchType(created && updated ? "CREATED_UPDATED" : created ? "CREATED" : "UPDATED");
            // 部件可以直达详情；文档/工程数据暂无独立详情路由，落在资源库列表（与检出卡片一致）
            if ("PART".equals(vo.getEntityType()) && vo.getOid() != null) {
                vo.setLinkPath("/part/" + vo.getOid());
            }
        }

        all.sort(Comparator.comparing(RecentObjectVO::getTouchedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all.size() > size ? new ArrayList<>(all.subList(0, size)) : all;
    }
}
