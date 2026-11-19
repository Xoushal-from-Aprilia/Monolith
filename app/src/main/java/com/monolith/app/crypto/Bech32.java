package com.monolith.app.crypto;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Bech32 (BIP-173) encoding used for npub / nsec keys (NIP-19). */
public final class Bech32 {

    private Bech32() {}

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GEN = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    private static int polymod(byte[] values) {
        int chk = 1;
        for (byte v : values) {
            int b = chk >>> 25;
            chk = ((chk & 0x1ffffff) << 5) ^ (v & 0xff);
            for (int i = 0; i < 5; i++) if (((b >>> i) & 1) == 1) chk ^= GEN[i];
        }
        return chk;
    }

    private static byte[] hrpExpand(String hrp) {
        byte[] out = new byte[hrp.length() * 2 + 1];
        for (int i = 0; i < hrp.length(); i++) {
            out[i] = (byte) (hrp.charAt(i) >> 5);
            out[i + hrp.length() + 1] = (byte) (hrp.charAt(i) & 31);
        }
        out[hrp.length()] = 0;
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] o = new byte[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    private static byte[] convertBits(byte[] data, int from, int to, boolean pad) {
        int acc = 0, bits = 0, maxv = (1 << to) - 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte d : data) {
            int value = d & 0xff;
            if ((value >>> from) != 0) throw new IllegalArgumentException("invalid data");
            acc = (acc << from) | value;
            bits += from;
            while (bits >= to) {
                bits -= to;
                out.write((acc >>> bits) & maxv);
            }
        }
        if (pad) {
            if (bits > 0) out.write((acc << (to - bits)) & maxv);
        } else if (bits >= from || ((acc << (to - bits)) & maxv) != 0) {
            throw new IllegalArgumentException("invalid padding");
        }
        return out.toByteArray();
    }

    public static String encode(String hrp, byte[] data) {
        byte[] five = convertBits(data, 8, 5, true);
        byte[] values = concat(hrpExpand(hrp), concat(five, new byte[6]));
        int pm = polymod(values) ^ 1;
        StringBuilder sb = new StringBuilder(hrp).append('1');
        for (byte b : five) sb.append(CHARSET.charAt(b));
        for (int i = 0; i < 6; i++) sb.append(CHARSET.charAt((pm >>> (5 * (5 - i))) & 31));
        return sb.toString();
    }

    /** returns {hrp, data} */
    public static Object[] decode(String str) {
        String s = str.trim().toLowerCase(Locale.ROOT);
        int pos = s.lastIndexOf('1');
        if (pos < 1 || pos + 7 > s.length()) throw new IllegalArgumentException("invalid bech32");
        String hrp = s.substring(0, pos);
        byte[] values = new byte[s.length() - pos - 1];
        for (int i = 0; i < values.length; i++) {
            int idx = CHARSET.indexOf(s.charAt(pos + 1 + i));
            if (idx < 0) throw new IllegalArgumentException("invalid char");
            values[i] = (byte) idx;
        }
        if (polymod(concat(hrpExpand(hrp), values)) != 1) throw new IllegalArgumentException("bad checksum");
        byte[] five = new byte[values.length - 6];
        System.arraycopy(values, 0, five, 0, five.length);
        return new Object[]{hrp, convertBits(five, 5, 8, false)};
    }
}
