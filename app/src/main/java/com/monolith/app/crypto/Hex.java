package com.monolith.app.crypto;

public final class Hex {
    private Hex() {}

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    public static String encode(byte[] b) {
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = DIGITS[(b[i] >> 4) & 0xf];
            out[i * 2 + 1] = DIGITS[b[i] & 0xf];
        }
        return new String(out);
    }

    public static byte[] decode(String s) {
        if (s.length() % 2 != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("invalid hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static boolean isHex64(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < 64; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }
}
