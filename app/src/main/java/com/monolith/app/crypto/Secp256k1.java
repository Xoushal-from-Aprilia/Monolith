package com.monolith.app.crypto;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Minimal, dependency-free secp256k1 implementation:
 * key generation, x-only public keys, BIP-340 Schnorr signatures and x-only ECDH.
 * Verified against the official BIP-340 and NIP-44 test vectors.
 */
public final class Secp256k1 {

    public static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
    public static final BigInteger N = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    private static final BigInteger GX = new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
    private static final BigInteger GY = new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);
    private static final BigInteger SEVEN = BigInteger.valueOf(7);
    private static final BigInteger SQRT_EXP = P.add(BigInteger.ONE).shiftRight(2);
    private static final SecureRandom RNG = new SecureRandom();

    private static final Jac G = new Jac(GX, GY, BigInteger.ONE);
    private static final Jac[] G_TABLE = buildTable(G);

    private Secp256k1() {}

    /** Jacobian point. Z == 0 means infinity. */
    private static final class Jac {
        final BigInteger x, y, z;
        Jac(BigInteger x, BigInteger y, BigInteger z) { this.x = x; this.y = y; this.z = z; }
        boolean inf() { return z.signum() == 0; }
    }

    private static final Jac INF = new Jac(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);

    private static BigInteger mod(BigInteger a) { return a.mod(P); }

    private static Jac dbl(Jac p) {
        if (p.inf() || p.y.signum() == 0) return INF;
        BigInteger ysq = mod(p.y.multiply(p.y));
        BigInteger s = mod(p.x.multiply(ysq).shiftLeft(2));
        BigInteger m = mod(p.x.multiply(p.x).multiply(BigInteger.valueOf(3)));
        BigInteger nx = mod(m.multiply(m).subtract(s.shiftLeft(1)));
        BigInteger ny = mod(m.multiply(s.subtract(nx)).subtract(ysq.multiply(ysq).shiftLeft(3)));
        BigInteger nz = mod(p.y.multiply(p.z).shiftLeft(1));
        return new Jac(nx, ny, nz);
    }

    private static Jac add(Jac p, Jac q) {
        if (p.inf()) return q;
        if (q.inf()) return p;
        BigInteger z1z1 = mod(p.z.multiply(p.z));
        BigInteger z2z2 = mod(q.z.multiply(q.z));
        BigInteger u1 = mod(p.x.multiply(z2z2));
        BigInteger u2 = mod(q.x.multiply(z1z1));
        BigInteger s1 = mod(p.y.multiply(q.z).multiply(z2z2));
        BigInteger s2 = mod(q.y.multiply(p.z).multiply(z1z1));
        if (u1.equals(u2)) {
            if (!s1.equals(s2)) return INF;
            return dbl(p);
        }
        BigInteger h = mod(u2.subtract(u1));
        BigInteger r = mod(s2.subtract(s1));
        BigInteger h2 = mod(h.multiply(h));
        BigInteger h3 = mod(h2.multiply(h));
        BigInteger u1h2 = mod(u1.multiply(h2));
        BigInteger nx = mod(r.multiply(r).subtract(h3).subtract(u1h2.shiftLeft(1)));
        BigInteger ny = mod(r.multiply(u1h2.subtract(nx)).subtract(s1.multiply(h3)));
        BigInteger nz = mod(h.multiply(p.z).multiply(q.z));
        return new Jac(nx, ny, nz);
    }

    private static Jac neg(Jac p) {
        if (p.inf()) return p;
        return new Jac(p.x, mod(P.subtract(p.y)), p.z);
    }

    /** 4-bit window table: [0]=INF, [i]=i*p */
    private static Jac[] buildTable(Jac p) {
        Jac[] t = new Jac[16];
        t[0] = INF;
        t[1] = p;
        for (int i = 2; i < 16; i++) t[i] = add(t[i - 1], p);
        return t;
    }

    private static Jac mul(Jac p, BigInteger k) {
        Jac[] table = (p == G) ? G_TABLE : buildTable(p);
        return mulWithTable(table, k);
    }

    private static Jac mulWithTable(Jac[] table, BigInteger k) {
        k = k.mod(N);
        Jac r = INF;
        int bits = k.bitLength();
        int windows = (bits + 3) / 4;
        for (int w = windows - 1; w >= 0; w--) {
            r = dbl(dbl(dbl(dbl(r))));
            int idx = 0;
            for (int b = 3; b >= 0; b--) {
                idx = (idx << 1) | (k.testBit(w * 4 + b) ? 1 : 0);
            }
            if (idx != 0) r = add(r, table[idx]);
        }
        return r;
    }

    /** returns {x, y} affine or null for infinity */
    private static BigInteger[] affine(Jac p) {
        if (p.inf()) return null;
        BigInteger zi = p.z.modInverse(P);
        BigInteger zi2 = mod(zi.multiply(zi));
        BigInteger x = mod(p.x.multiply(zi2));
        BigInteger y = mod(p.y.multiply(zi2).multiply(zi));
        return new BigInteger[]{x, y};
    }

    private static Jac liftX(BigInteger x) {
        if (x.signum() < 0 || x.compareTo(P) >= 0) return null;
        BigInteger c = mod(x.modPow(BigInteger.valueOf(3), P).add(SEVEN));
        BigInteger y = c.modPow(SQRT_EXP, P);
        if (!mod(y.multiply(y)).equals(c)) return null;
        if (y.testBit(0)) y = P.subtract(y);
        return new Jac(x, y, BigInteger.ONE);
    }

    // ---------------------------------------------------------------- byte helpers

    public static byte[] to32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static BigInteger num(byte[] b) { return new BigInteger(1, b); }

    private static byte[] slice(byte[] b, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(b, from, out, 0, out.length);
        return out;
    }

    public static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) md.update(p);
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] taggedHash(String tag, byte[]... parts) {
        byte[] th = sha256(tag.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[][] all = new byte[parts.length + 2][];
        all[0] = th;
        all[1] = th;
        System.arraycopy(parts, 0, all, 2, parts.length);
        return sha256(all);
    }

    // ---------------------------------------------------------------- public API

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static boolean isValidPrivateKey(byte[] sk) {
        if (sk == null || sk.length != 32) return false;
        BigInteger d = num(sk);
        return d.signum() > 0 && d.compareTo(N) < 0;
    }

    public static byte[] generatePrivateKey() {
        while (true) {
            byte[] sk = randomBytes(32);
            if (isValidPrivateKey(sk)) return sk;
        }
    }

    /** 32-byte x-only public key. */
    public static byte[] publicKey(byte[] sk) {
        BigInteger d = num(sk);
        if (d.signum() == 0 || d.compareTo(N) >= 0) throw new IllegalArgumentException("invalid private key");
        BigInteger[] a = affine(mul(G, d));
        return to32(a[0]);
    }

    public static byte[] signSchnorr(byte[] msg, byte[] sk) {
        return signSchnorr(msg, sk, randomBytes(32));
    }

    public static byte[] signSchnorr(byte[] msg, byte[] sk, byte[] aux) {
        BigInteger d0 = num(sk);
        if (d0.signum() == 0 || d0.compareTo(N) >= 0) throw new IllegalArgumentException("invalid private key");
        BigInteger[] pa = affine(mul(G, d0));
        BigInteger d = pa[1].testBit(0) ? N.subtract(d0) : d0;
        byte[] pBytes = to32(pa[0]);
        byte[] auxHash = taggedHash("BIP0340/aux", aux);
        byte[] dBytes = to32(d);
        byte[] t = new byte[32];
        for (int i = 0; i < 32; i++) t[i] = (byte) (dBytes[i] ^ auxHash[i]);
        byte[] rand = taggedHash("BIP0340/nonce", t, pBytes, msg);
        BigInteger k0 = num(rand).mod(N);
        if (k0.signum() == 0) throw new IllegalStateException("bad nonce");
        BigInteger[] ra = affine(mul(G, k0));
        BigInteger k = ra[1].testBit(0) ? N.subtract(k0) : k0;
        byte[] rBytes = to32(ra[0]);
        BigInteger e = num(taggedHash("BIP0340/challenge", rBytes, pBytes, msg)).mod(N);
        byte[] sig = new byte[64];
        System.arraycopy(rBytes, 0, sig, 0, 32);
        System.arraycopy(to32(k.add(e.multiply(d)).mod(N)), 0, sig, 32, 32);
        return sig;
    }

    public static boolean verifySchnorr(byte[] msg, byte[] pub, byte[] sig) {
        try {
            if (pub == null || pub.length != 32 || sig == null || sig.length != 64) return false;
            Jac pp = liftX(num(pub));
            if (pp == null) return false;
            BigInteger r = num(slice(sig, 0, 32));
            BigInteger s = num(slice(sig, 32, 64));
            if (r.compareTo(P) >= 0 || s.compareTo(N) >= 0) return false;
            BigInteger e = num(taggedHash("BIP0340/challenge", slice(sig, 0, 32), pub, msg)).mod(N);
            Jac rr = add(mul(G, s), neg(mul(pp, e)));
            BigInteger[] ra = affine(rr);
            if (ra == null) return false;
            if (ra[1].testBit(0)) return false;
            return ra[0].equals(r);
        } catch (Exception ex) {
            return false;
        }
    }

    /** x coordinate of sk * lift_x(pub) (unhashed), as used by NIP-04 / NIP-44. */
    public static byte[] ecdhX(byte[] sk, byte[] pub) {
        Jac pp = liftX(num(pub));
        if (pp == null) throw new IllegalArgumentException("invalid public key");
        BigInteger d = num(sk);
        if (d.signum() == 0 || d.compareTo(N) >= 0) throw new IllegalArgumentException("invalid private key");
        BigInteger[] a = affine(mul(pp, d));
        if (a == null) throw new IllegalArgumentException("invalid shared point");
        return to32(a[0]);
    }

    public static boolean isValidPublicKey(byte[] pub) {
        return pub != null && pub.length == 32 && liftX(num(pub)) != null;
    }
}
