package com.meper.chatbi.storage.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 凭据加密服务（AES-256-GCM）。
 *
 * <p>主密钥来自配置 {@code meper.security.master-key}（环境变量 MEPER_MASTER_KEY，base64 编码 32 字节），
 * 缺失即启动失败 —— 不生成临时密钥，避免凭据在重启后静默失效。
 * 主密钥不落库、不进 git；密文每次加密使用随机 IV。
 */
@Component
public class AesGcmCipherService {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipherService(@Value("${meper.security.master-key}") String base64MasterKey) {
        byte[] decoded = Base64.getDecoder().decode(base64MasterKey.trim());
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException("主密钥长度必须为 32 字节（base64 编码），当前: " + decoded.length);
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** 加密产物（ciphertext + iv，均以二进制存控制库）。 */
    public record EncryptedSecret(byte[] ciphertext, byte[] iv) {
    }

    public EncryptedSecret encrypt(char[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(new String(plaintext).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new EncryptedSecret(ciphertext, iv);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    public char[] decrypt(EncryptedSecret secret) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, secret.iv()));
            byte[] plain = cipher.doFinal(secret.ciphertext());
            char[] result = new String(plain, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
            Arrays.fill(plain, (byte) 0);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败（主密钥不匹配或数据损坏）", e);
        }
    }
}
