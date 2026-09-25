/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ai.service.api;

import java.util.List;
import java.util.Map;

/**
 * CK-PLM 助手服务契约。
 *
 * <p>对接 OpenAI 兼容的模型服务（WorkBuddy 的账号密码登录、各家的 api-key、本地模型都可以），
 * 并通过 function calling 让模型去查 CK-PLM 的真实数据（见 {@code PlmTools}）。
 *
 * <p>模型可以有多个（配置见 {@code plm.ai.models}）：{@link #chat} 的 {@code modelId}
 * 指定用哪个，{@link #status()} 给出可选清单供前端做下拉。
 */
public interface AiChatService {

    /**
     * 助手状态（供前端决定是否显示入口、模型下拉有哪些选项、以及"不能用时"该提示什么）。
     *
     * @return { enabled, configured, state, hint, defaultModel, model, authMode,
     *           models: [{ id, label, model, authMode }] }
     *         {@code state} ∈ ready / disabled（总开关关着）/ no-models（没有可用模型）；
     *         {@code hint} 是可直接显示的一句说明（含该改哪个配置键），前端不必自己拼文案
     */
    Map<String, Object> status();

    /**
     * 一轮对话。
     *
     * @param message 用户这句话
     * @param history 最近几轮上下文（[{role, content}]，可空）
     * @param modelId 用哪个模型（{@code plm.ai.models[].id}）；空 = 用默认模型
     * @return { answer: 回答文本, tools: [调用过的工具名], configured: 是否已配置 }
     */
    Map<String, Object> chat(String message, List<Map<String, String>> history, String modelId);
}
