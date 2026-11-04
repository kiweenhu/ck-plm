/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 源件下载地址的签名与校验 —— 没有共享存储时，转换器靠这个地址回拉 STEP。
 *
 * <h3>为什么要在令牌之外再签一层</h3>
 * {@code X-Cad-Forge-Token} 只能证明"调用方是转换器"，但地址本身会落进转换器的
 * 任务文件（{@code .job.json}）和日志里 —— 谁拿到日志谁就能下载源件。
 * 签名里带过期时间，把这条地址的寿命限制在"这次转换用得上"的范围内。
 *
 * <h3>为什么签的是转换记录 oid 而不是文件 oid</h3>
 * 按记录签，服务端能反查出"这条记录要转的到底是哪个文件"，
 * 于是天然做不到"换个 oid 拖走别人的图档" —— 越权面直接消失。
 *
 * <h3>为什么比较必须常量时间</h3>
 * 用 {@code equals} 会因提前返回而泄漏匹配前缀长度，
 * 与 {@code CadConvertCallbackController} 的令牌比较是同一个道理。
 */
public final class SourceUrlSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    private SourceUrlSigner() {
    }

    /** 生成签名。token 为空时抛异常 —— 无令牌的地址不签，宁可让转换器走别的取源路径。 */
    public static String sign(String token, String visOid, long expEpochSec) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("缺少服务间共享令牌，无法生成源件地址签名");
        }
        return hmac(token, payload(visOid, expEpochSec));
    }

    /**
     * 校验签名与有效期。
     *
     * @param exp 过期时间（Epoch 秒，字符串形式，来自 URL 参数）
     */
    public static boolean verify(String token, String visOid, String exp, String sig) {
        if (token == null || token.isBlank() || visOid == null || visOid.isBlank()) {
            return false;
        }
        long expSec;
        try {
            expSec = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (expSec <= 0 || System.currentTimeMillis() / 1000L > expSec) {
            return false; // 过期
        }
        if (sig == null || sig.isBlank()) {
            return false;
        }
        return constantTimeEquals(hmac(token, payload(visOid, expSec)), sig);
    }

    private static String payload(String visOid, long expEpochSec) {
        return visOid + ":" + expEpochSec;
    }

    private static String hmac(String token, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("生成源件地址签名失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
