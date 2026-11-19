package com.monolith.app.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * NIP-44 v2 authenticated encryption (secp256k1 ECDH + HKDF + ChaCha20 + HMAC-SHA256).
 * Implemented from the specification and checked against the official test vectors.
 */
public final class Nip44 {

    private Nip44() {}

    private static final byte[] SALT = "nip44-v2".getBytes(StandardCharsets.UTF_8);

    public static byte[] hmac(byte[] key, byte[]... parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            for (byte[] p : parts) mac.update(p);
            return mac.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] conversationKey(byte[] privateKey, byte[] publicKeyX) {
        byte[] shared = Secp256k1.ecdhX(privateKey, publicKeyX);
        return hmac(SALT, shared);
    }

    /** HKDF-expand(prk, info, 76) -> chacha key(32) | chacha nonce(12) | hmac key(32) */
    public static byte[][] messageKeys(byte[] conversationKey, byte[] nonce) {
        byte[] okm = new byte[76];
        byte[] prev = new byte[0];
        int pos = 0;
        int counter = 1;
        while (pos < 76) {
            prev = hmac(conversationKey, prev, nonce, new byte[]{(byte) counter});
            int n = Math.min(prev.length, 76 - pos);
            System.arraycopy(prev, 0, okm, pos, n);
            pos += n;
            counter++;
        }
        byte[] k = new byte[32];
        byte[] cn = new byte[12];
        byte[] hk = new byte[32];
        System.arraycopy(okm, 0, k, 0, 32);
        System.arraycopy(okm, 32, cn, 0, 12);
        System.arraycopy(okm, 44, hk, 0, 32);
        return new byte[][]{k, cn, hk};
    }

    public static int calcPaddedLen(int len) {
        if (len <= 32) return 32;
        int nextPower = 1 << (32 - Integer.numberOfLeadingZeros(len - 1));
        int chunk = nextPower <= 256 ? 32 : nextPower / 8;
        return chunk * (((len - 1) / chunk) + 1);
    }

    private static byte[] pad(byte[] plain) {
        int len = plain.length;
        if (len < 1 || len > 65535) throw new IllegalArgumentException("invalid plaintext length " + len);
        byte[] out = new byte[2 + calcPaddedLen(len)];
        out[0] = (byte) ((len >> 8) & 0xff);
        out[1] = (byte) (len & 0xff);
        System.arraycopy(plain, 0, out, 2, len);
        return out;
    }

    private static byte[] unpad(byte[] padded) {
        int len = ((padded[0] & 0xff) << 8) | (padded[1] & 0xff);
        if (len < 1 || len > 65535 || padded.length != 2 + calcPaddedLen(len) || 2 + len > padded.length) {
            throw new IllegalArgumentException("invalid padding");
        }
        byte[] out = new byte[len];
        System.arraycopy(padded, 2, out, 0, len);
        return out;
    }

    public static String encrypt(String plaintext, byte[] conversationKey) {
        return encrypt(plaintext, conversationKey, Secp256k1.randomBytes(32));
    }

    public static String encrypt(String plaintext, byte[] conversationKey, byte[] nonce) {
        byte[][] keys = messageKeys(conversationKey, nonce);
        byte[] padded = pad(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] ct = ChaCha20.xor(keys[0], keys[1], 0, padded);
        byte[] mac = hmac(keys[2], nonce, ct);
        byte[] out = new byte[1 + 32 + ct.length + 32];
        out[0] = 2;
        System.arraycopy(nonce, 0, out, 1, 32);
        System.arraycopy(ct, 0, out, 33, ct.length);
        System.arraycopy(mac, 0, out, 33 + ct.length, 32);
        return Base64.getEncoder().encodeToString(out);
    }

    public static String decrypt(String payload, byte[] conversationKey) {
        int plen = payload.length();
        if (plen == 0 || payload.charAt(0) == '#') throw new IllegalArgumentException("unknown version");
        if (plen < 132 || plen > 87472) throw new IllegalArgumentException("invalid payload size");
        byte[] data = Base64.getDecoder().decode(payload);
        int dlen = data.length;
        if (dlen < 99 || dlen > 65603) throw new IllegalArgumentException("invalid data size");
        if (data[0] != 2) throw new IllegalArgumentException("unknown version " + data[0]);
        byte[] nonce = new byte[32];
        System.arraycopy(data, 1, nonce, 0, 32);
        byte[] ct = new byte[dlen - 65];
        System.arraycopy(data, 33, ct, 0, ct.length);
        byte[] mac = new byte[32];
        System.arraycopy(data, dlen - 32, mac, 0, 32);
        byte[][] keys = messageKeys(conversationKey, nonce);
        byte[] calc = hmac(keys[2], nonce, ct);
        if (!MessageDigest.isEqual(calc, mac)) throw new IllegalArgumentException("invalid MAC");
        byte[] padded = ChaCha20.xor(keys[0], keys[1], 0, ct);
        return new String(unpad(padded), StandardCharsets.UTF_8);
    }
}
