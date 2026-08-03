/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * 密码编码器 —— 基于 PBKDF2WithHmacSHA256 的密码哈希与校验。
 *
 * <p>每次加密生成随机盐值，盐值与哈希结果拼接存储，
 * 格式：{@code salt:hash}（Base64 编码）。
 *
 * <p>不依赖第三方库，仅使用 JDK 内置 {@code javax.crypto}。
 */
public final class PasswordEncoder {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final String SEPARATOR = ":";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordEncoder() {
    }

    /**
     * 对明文密码进行哈希编码。
     *
     * @param rawPassword 明文密码
     * @return 编码后的密文（salt:hash）
     */
    public static String encode(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        byte[] hash = hash(rawPassword.toCharArray(), salt);

        return Base64.getEncoder().encodeToString(salt)
                + SEPARATOR
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验明文密码是否匹配已编码的密文。
     *
     * @param rawPassword    明文密码
     * @param encodedPassword 已编码密文（salt:hash）
     * @return true 匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }

        String[] parts = encodedPassword.split(SEPARATOR);
        if (parts.length != 2) {
            return false;
        }

        byte[] salt;
        byte[] expectedHash;
        try {
            salt = Base64.getDecoder().decode(parts[0]);
            expectedHash = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }

        byte[] actualHash = hash(rawPassword.toCharArray(), salt);

        // 恒定时间比较（防时序攻击）
        return slowEquals(expectedHash, actualHash);
    }

    private static byte[] hash(char[] password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("密码编码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 恒定时间字节数组比较，防止时序攻击。
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
