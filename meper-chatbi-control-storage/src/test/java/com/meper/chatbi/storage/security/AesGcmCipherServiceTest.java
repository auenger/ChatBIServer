package com.meper.chatbi.storage.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCipherServiceTest {

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void encryptDecryptRoundtrip() {
        var service = new AesGcmCipherService(randomKey());
        char[] secret = "p@ssw0rd-密码-123".toCharArray();
        var encrypted = service.encrypt(secret);
        assertFalse(new String(encrypted.ciphertext()).contains("p@ssw0rd"), "密文不得含明文");
        assertArrayEquals(secret, service.decrypt(encrypted));
    }

    @Test
    void randomIvPerEncryption() {
        var service = new AesGcmCipherService(randomKey());
        var a = service.encrypt("same".toCharArray());
        var b = service.encrypt("same".toCharArray());
        assertFalse(java.util.Arrays.equals(a.iv(), b.iv()));
        assertFalse(java.util.Arrays.equals(a.ciphertext(), b.ciphertext()));
    }

    @Test
    void wrongKeyFailsDecrypt() {
        var encryptor = new AesGcmCipherService(randomKey());
        var decryptor = new AesGcmCipherService(randomKey());
        var encrypted = encryptor.encrypt("secret".toCharArray());
        assertThrows(IllegalStateException.class, () -> decryptor.decrypt(encrypted));
    }

    @Test
    void rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new AesGcmCipherService(shortKey));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmCipherService("not-base64!!!"));
    }
}
