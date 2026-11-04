/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.dto.ProcessStartOptionVO;

/**
 * 「发起流程」入口的解析服务。
 *
 * <p>把"该业务对象该发起哪个流程"这件事收口成一处理：
 * 业务对象的类型编码 + 状态 → 类型模块里配置的流程模板 → 该模板的最新版本信息。
 * 所有业务对象的行操作下拉（零组件 / 文档 / 工程数据 …）都走这一个接口，
 * 前端也复用同一个弹框组件，避免各页面各写一套。
 */
public interface ProcessStartOptionService {

    /**
     * 解析发起选项。
     *
     * @param typeDefinitionCode 业务对象类型编码（如 {@code PART} / {@code DOCUMENT} / {@code ENG_DOCUMENT}）
     * @param statusCode         业务对象当前生命周期状态编码（如 {@code DRAFT}）
     * @param iterationOid       业务对象迭代固化的生命周期模板子版本 oid（可空；
     *                           传了就优先按"对象出生时那一版"的配置解析，见
     *                           {@code TypeLifecycleStateProcessService#resolveLink}）
     * @param entityOid          业务对象主 oid（可空）。传了就一并判定<b>该对象在该大版本下
     *                           是否已有流程在执行</b> —— 同一对象 + 同一大版本同时只允许一个流程
     *                           （大版本之间独立），有在跑的则 {@code startable = false}
     * @return 解析结果；<b>未配置 / 模板已删 / 尚未部署 / 该版本已有流程在执行</b>时同样返回结果，
     *         只是 {@code startable = false} 且带 {@code reason}
     * @throws IllegalArgumentException 类型编码为空 / 类型不存在 / 状态为空
     */
    ProcessStartOptionVO resolve(String typeDefinitionCode, String statusCode, String iterationOid,
                                 String entityOid);

    /**
     * 按<b>流程 key 直传</b>解析发起选项 —— 用于"不经过类型绑定"的发起入口
     * （如产品系列页的阶段门 / 评审行：行上自带 {@code process_key}，点「发起」就是它）。
     *
     * <p>与 {@link #resolve} 的区别只有"模板从哪来"：这里不再查类型的状态-流程绑定，
     * 直接按 key 找模板；<b>部署判定与冲突闸门完全同口径</b>（组装逻辑共用一份），
     * 所以两种入口的 {@code startable} 语义不会漂移。
     *
     * @param processKey  流程模板 key（如 {@code technical-review} / {@code gate-decision}）
     * @param typeCode    发起上下文业务对象的类型编码（可空，仅回显与冲突判定用）
     * @param statusCode  发起上下文业务对象的当前状态（可空，仅回显）
     * @param entityOid   发起上下文业务对象 oid（可空）。传了则同样判定"该对象该大版本下是否已有流程在执行"
     * @return 解析结果；模板不存在 / 未部署 / 有冲突时 {@code startable = false} 且带 {@code reason}
     * @throws IllegalArgumentException processKey 为空
     */
    ProcessStartOptionVO resolveByKey(String processKey, String typeCode, String statusCode, String entityOid);
}
