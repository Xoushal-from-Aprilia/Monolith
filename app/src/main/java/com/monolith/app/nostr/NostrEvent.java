package com.monolith.app.nostr;

import com.monolith.app.crypto.Hex;
import com.monolith.app.crypto.Secp256k1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A Nostr event (NIP-01) with canonical serialization, id computation, signing and verification. */
public final class NostrEvent {
    public String id = "";
    public String pubkey = "";
    public long createdAt;
    public int kind;
    public List<List<String>> tags = new ArrayList<>();
    public String content = "";
    public String sig = "";

    public NostrEvent() {}

    public NostrEvent(String pubkey, long createdAt, int kind, List<List<String>> tags, String content) {
        this.pubkey = pubkey;
        this.createdAt = createdAt;
        this.kind = kind;
        this.tags = tags;
        this.content = content;
    }

    public static void escape(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void appendTags(StringBuilder sb, List<List<String>> tags) {
        sb.append('[');
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            List<String> t = tags.get(i);
            sb.append('[');
            for (int j = 0; j < t.size(); j++) {
                if (j > 0) sb.append(',');
                escape(sb, t.get(j));
            }
            sb.append(']');
        }
        sb.append(']');
    }

    public String serializeForId() {
        StringBuilder sb = new StringBuilder();
        sb.append("[0,");
        escape(sb, pubkey);
        sb.append(',').append(createdAt).append(',').append(kind).append(',');
        appendTags(sb, tags);
        sb.append(',');
        escape(sb, content);
        sb.append(']');
        return sb.toString();
    }

    public byte[] computeIdBytes() {
        return Secp256k1.sha256(serializeForId().getBytes(StandardCharsets.UTF_8));
    }

    public NostrEvent sign(byte[] privateKey) {
        this.pubkey = Hex.encode(Secp256k1.publicKey(privateKey));
        byte[] idb = computeIdBytes();
        this.id = Hex.encode(idb);
        this.sig = Hex.encode(Secp256k1.signSchnorr(idb, privateKey));
        return this;
    }

    public boolean verify() {
        try {
            byte[] idb = computeIdBytes();
            if (!Hex.encode(idb).equals(id)) return false;
            return Secp256k1.verifySchnorr(idb, Hex.decode(pubkey), Hex.decode(sig));
        } catch (Exception e) {
            return false;
        }
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":");
        escape(sb, id);
        sb.append(",\"pubkey\":");
        escape(sb, pubkey);
        sb.append(",\"created_at\":").append(createdAt);
        sb.append(",\"kind\":").append(kind);
        sb.append(",\"tags\":");
        appendTags(sb, tags);
        sb.append(",\"content\":");
        escape(sb, content);
        sb.append(",\"sig\":");
        escape(sb, sig);
        sb.append('}');
        return sb.toString();
    }

    public String firstTag(String name) {
        for (List<String> t : tags) {
            if (t.size() >= 2 && name.equals(t.get(0))) return t.get(1);
        }
        return null;
    }
}
