/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.controller;

import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.mapper.ChangeRequestMapper;
import cn.ck.plm.change.mapper.ChangeTaskMapper;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变更单据反查 —— 按 oid 认出这是变更请求 / 变更通告 / 变更任务中的哪一张。
 *
 * <h3>为什么要这个接口</h3>
 * <p>变更单据<b>不是</b>软类型实例，发起审批时没有 {@code ck_process_entity_set} 关联行，
 * 流程侧只拿得到一个 businessKey（= 单据 oid）。于是任务中心 / 流程监控的
 * 「业务对象」一栏对变更类任务永远是空的 —— 一行只有「设置审批人 + 流程名」，
 * 而同一个流程会挂在几十张单据上，不点进去根本不知道办的是哪一条。
 *
 * <p>流程模块刻意不反向依赖业务模块（见 {@code ProcessServiceImpl#entityRefsOf}），
 * 所以 businessKey → 单据的翻译放在这里：流程侧只交出 businessKey，前端拿它来问一次。
 * 认不出来（不是变更单据）就 404，调用方按"没有业务对象"处理。
 *
 * <h3>为什么三类单据共用一条接口</h3>
 * <p>三类 oid 都是 UUID，不会撞号；分成三条接口会让调用方先猜类型再发请求，
 * 猜错一次就是多一次 404。
 */
@RestController
@RequestMapping("/api/plm/change-documents")
public class ChangeDocumentController {

    private final ChangeRequestMapper requestMapper;
    private final ChangeNoticeMapper noticeMapper;
    private final ChangeTaskMapper taskMapper;

    public ChangeDocumentController(ChangeRequestMapper requestMapper,
                                    ChangeNoticeMapper noticeMapper,
                                    ChangeTaskMapper taskMapper) {
        this.requestMapper = requestMapper;
        this.noticeMapper = noticeMapper;
        this.taskMapper = taskMapper;
    }

    /**
     * 按 oid 反查变更单据。
     *
     * @return {@code { kind: REQUEST|NOTICE|TASK, oid, code, name, status }}；找不到返回 404
     */
    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String oid) {
        ChangeRequest request = requestMapper.selectByOid(oid);
        if (request != null) {
            return ApiResponse.ok(ref("REQUEST", request.getOid(), request.getCode(),
                    request.getName(), request.getStatus()));
        }
        ChangeNotice notice = noticeMapper.selectByOid(oid);
        if (notice != null) {
            return ApiResponse.ok(ref("NOTICE", notice.getOid(), notice.getCode(),
                    notice.getName(), notice.getStatus()));
        }
        ChangeTask task = taskMapper.selectByOid(oid);
        if (task != null) {
            return ApiResponse.ok(ref("TASK", task.getOid(), task.getCode(),
                    task.getName(), task.getStatus()));
        }
        return ApiResponse.fail(404, "按 oid 找不到变更单据：" + oid);
    }

    private Map<String, Object> ref(String kind, String oid, String code, String name, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("oid", oid);
        result.put("code", code);
        result.put("name", name);
        result.put("status", status);
        return result;
    }
}
