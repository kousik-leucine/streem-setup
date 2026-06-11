package com.leucine.setup.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * AES-256-GCM encrypt/decrypt for short secrets (connection passwords).
 * Output layout: [12-byte IV | ciphertext | 16-byte GCM tag].
 */
@Component
public class AesGcm {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final LocalKeyStore keyStore;
  private final SecureRandom random = new SecureRandom();

  public AesGcm(LocalKeyStore keyStore) {
    this.keyStore = keyStore;
  }

  public byte[] encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keyStore.key(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      return ByteBuffer.allocate(iv.length + ciphertext.length)
          .put(iv).put(ciphertext).array();
    } catch (Exception e) {
      throw new IllegalStateException("Encrypt failed", e);
    }
  }

  public String decrypt(byte[] payload) {
    try {
      ByteBuffer buf = ByteBuffer.wrap(payload);
      byte[] iv = new byte[IV_BYTES];
      buf.get(iv);
      byte[] ciphertext = new byte[buf.remaining()];
      buf.get(ciphertext);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, keyStore.key(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] plain = cipher.doFinal(ciphertext);
      return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Decrypt failed", e);
    }
  }
}
