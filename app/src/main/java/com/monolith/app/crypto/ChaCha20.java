package com.monolith.app.crypto;

/** RFC 8439 ChaCha20 stream cipher (96-bit nonce, 32-bit counter). */
public final class ChaCha20 {

    private ChaCha20() {}

    private static int rotl(int v, int c) { return (v << c) | (v >>> (32 - c)); }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static void qr(int[] x, int a, int b, int c, int d) {
        x[a] += x[b]; x[d] = rotl(x[d] ^ x[a], 16);
        x[c] += x[d]; x[b] = rotl(x[b] ^ x[c], 12);
        x[a] += x[b]; x[d] = rotl(x[d] ^ x[a], 8);
        x[c] += x[d]; x[b] = rotl(x[b] ^ x[c], 7);
    }

    public static byte[] xor(byte[] key, byte[] nonce, int counter, byte[] input) {
        if (key.length != 32 || nonce.length != 12) throw new IllegalArgumentException("bad key/nonce");
        int[] state = new int[16];
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;
        for (int i = 0; i < 8; i++) state[4 + i] = le32(key, i * 4);
        state[12] = counter;
        state[13] = le32(nonce, 0);
        state[14] = le32(nonce, 4);
        state[15] = le32(nonce, 8);

        byte[] out = new byte[input.length];
        int[] x = new int[16];
        byte[] block = new byte[64];
        int pos = 0;
        while (pos < input.length) {
            System.arraycopy(state, 0, x, 0, 16);
            for (int i = 0; i < 10; i++) {
                qr(x, 0, 4, 8, 12);
                qr(x, 1, 5, 9, 13);
                qr(x, 2, 6, 10, 14);
                qr(x, 3, 7, 11, 15);
                qr(x, 0, 5, 10, 15);
                qr(x, 1, 6, 11, 12);
                qr(x, 2, 7, 8, 13);
                qr(x, 3, 4, 9, 14);
            }
            for (int i = 0; i < 16; i++) {
                int v = x[i] + state[i];
                block[i * 4] = (byte) v;
                block[i * 4 + 1] = (byte) (v >>> 8);
                block[i * 4 + 2] = (byte) (v >>> 16);
                block[i * 4 + 3] = (byte) (v >>> 24);
            }
            int n = Math.min(64, input.length - pos);
            for (int i = 0; i < n; i++) out[pos + i] = (byte) (input[pos + i] ^ block[i]);
            pos += n;
            state[12]++;
        }
        return out;
    }
}
