package com.rivers.user.util;

import java.security.SecureRandom;
import java.util.Random;

public class PasswordGenerator {

    // 定义字符集
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    private static final String ALL_CHARS = LETTERS + DIGITS + SPECIAL_CHARS;

    // 共享的Random实例
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static String generateComplexPassword(int length) {
        if (length < 4) {
            throw new IllegalArgumentException("密码长度至少为4位，以确保包含各种字符类型");
        }

        StringBuilder password = new StringBuilder();

        // 确保至少包含一个字母、数字和特殊字符
        password.append(LETTERS.charAt(SECURE_RANDOM.nextInt(LETTERS.length())));
        password.append(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        password.append(SPECIAL_CHARS.charAt(SECURE_RANDOM.nextInt(SPECIAL_CHARS.length())));

        // 填充剩余长度
        for (int i = 3; i < length; i++) {
            password.append(ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length())));
        }

        // 打乱字符顺序
        return shuffleString(password.toString(), SECURE_RANDOM);
    }

    private static String shuffleString(String string, Random random) {
        char[] chars = string.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
}
