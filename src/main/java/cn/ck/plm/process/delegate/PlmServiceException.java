/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

/**
 * 自动服务 / 通知节点执行失败 —— 消息是<b>给办理人看的</b>，不是给日志看的。
 *
 * <p>为什么单独一个异常：这类失败与"代码有 bug"不同，多数是配置问题
 * （目标状态不在模板内、对象已被别人检出、服务尚未实现…），
 * 消息会原样出现在办理失败的提示里，所以要写清"哪个节点、哪个对象、为什么"。
 */
public class PlmServiceException extends RuntimeException {

    public PlmServiceException(String message) {
        super(message);
    }

    public PlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
