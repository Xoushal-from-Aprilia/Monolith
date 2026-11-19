package com.monolith.app.nostr

import com.monolith.app.core.Keys
import com.monolith.app.core.nowSec
import com.monolith.app.crypto.Hex
import com.monolith.app.crypto.Nip44
import com.monolith.app.crypto.Secp256k1
import org.json.JSONObject

/**
 * Private envelope, modelled after NIP-59 gift wraps:
 *  - the payload is put in an inner event signed by the real sender (authentication),
 *  - the inner event is NIP-44 encrypted to the recipient with a throw-away key,
 *  - the outer event is signed by that throw-away key, so relays can't see who is talking to whom.
 */
object Envelope {
    const val KIND_WRAP = 1059          // stored by relays (store-and-forward mailbox)
    const val KIND_EPHEMERAL = 21059    // forwarded live, never stored (signaling, location, typing)
    const val KIND_INNER = 14059        // never published on its own
    const val KIND_PROFILE = 30078      // public, replaceable: username directory

    class Inner(val from: String, val id: String, val createdAt: Long, val payload: JSONObject)

    fun wrap(me: Keys, to: String, payload: JSONObject, ephemeral: Boolean, ttlSec: Long?): NostrEvent {
        val now = nowSec()
        val inner = NostrEvent("", now, KIND_INNER, listOf(listOf("p", to)), payload.toString()).sign(me.priv)
        val throwaway = Secp256k1.generatePrivateKey()
        val ck = Nip44.conversationKey(throwaway, Hex.decode(to))
        val content = Nip44.encrypt(inner.toJson(), ck)
        val tags = mutableListOf(listOf("p", to))
        if (ttlSec != null) tags.add(listOf("expiration", (now + ttlSec).toString()))
        return NostrEvent("", now, if (ephemeral) KIND_EPHEMERAL else KIND_WRAP, tags, content).sign(throwaway)
    }

    fun unwrap(me: Keys, wrap: NostrEvent): Inner? = try {
        val ck = Nip44.conversationKey(me.priv, Hex.decode(wrap.pubkey))
        val json = Nip44.decrypt(wrap.content, ck)
        val inner = parseEvent(JSONObject(json))
        if (inner.kind != KIND_INNER || !inner.verify() || inner.firstTag("p") != me.pub) {
            null
        } else {
            Inner(inner.pubkey, inner.id, inner.createdAt, JSONObject(inner.content))
        }
    } catch (e: Exception) {
        null
    }
}
