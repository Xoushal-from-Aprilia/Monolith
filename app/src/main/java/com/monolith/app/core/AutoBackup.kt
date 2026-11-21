package com.monolith.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.monolith.app.service.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Automatic backup: once a week the same encrypted file of the manual backup is written to a
 * folder the user picked (it can be a cloud folder offered by the system picker). The password
 * stays on the phone, locked by the Android keystore. The last [KEEP] copies are kept.
 */
object AutoBackup {
    private const val KEEP = 4
    private const val ALIAS = "monolith_autobackup"
    private const val WEEK = 7 * 24 * 3_600_000L

    data class State(val on: Boolean, val folder: String?, val last: Long, val error: Boolean)

    private val _state = MutableStateFlow(State(false, null, 0L, false))
    val state: StateFlow<State> = _state.asStateFlow()

    private fun sp(ctx: Context) = ctx.getSharedPreferences("autobackup", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = sp(ctx)
        _state.value = State(p.getBoolean("on", false), p.getString("tree", null), p.getLong("last", 0L), p.getBoolean("err", false))
    }

    /** readable name of the chosen folder */
    fun folderName(uri: String?): String? = uri?.let {
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)).substringAfterLast(':').ifBlank { "/" } }.getOrNull()
    }

    fun enable(ctx: Context, tree: Uri, password: CharArray) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        sp(ctx).edit()
            .putBoolean("on", true)
            .putString("tree", tree.toString())
            .putString("pw", encrypt(String(password).toByteArray()))
            .putBoolean("err", false)
            .apply()
        load(ctx)
        schedule(ctx, soon = true)
    }

    fun disable(ctx: Context) {
        val p = sp(ctx)
        p.getString("tree", null)?.let { t ->
            runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(t), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        p.edit().putBoolean("on", false).remove("pw").remove("tree").apply()
        cancel(ctx)
        load(ctx)
    }

    fun clear(ctx: Context) {
        disable(ctx)
        sp(ctx).edit().clear().apply()
        load(ctx)
    }

    private fun intent(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_BACKUP)
        return PendingIntent.getBroadcast(ctx, 9092, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cancel(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx)) }
    }

    /** next run: a week after the last backup (a minute from now when it is overdue) */
    fun schedule(ctx: Context, soon: Boolean = false) {
        val p = sp(ctx)
        if (!p.getBoolean("on", false)) return
        val last = p.getLong("last", 0L)
        val at = if (soon || last == 0L) now() + 60_000L else maxOf(last + WEEK, now() + 60_000L)
        runCatching { ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(ctx)) }
    }

    /** writes one backup now; returns true when it worked */
    suspend fun run(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val p = sp(ctx)
        val ok = runCatching {
            if (!p.getBoolean("on", false)) return@runCatching false
            val tree = Uri.parse(p.getString("tree", null) ?: return@runCatching false)
            val pw = String(decrypt(p.getString("pw", null) ?: return@runCatching false)).toCharArray()
            val cr = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val name = "monolith-auto-$day.mbk"
            // same day twice: replace
            children(ctx, tree).filter { it.second == name }.forEach { runCatching { DocumentsContract.deleteDocument(cr, it.first) } }
            val doc = DocumentsContract.createDocument(cr, parent, "application/octet-stream", name) ?: return@runCatching false
            cr.openOutputStream(doc, "w")?.use { out -> com.monolith.app.core.Backup.export(ctx, out, pw) } ?: return@runCatching false
            pw.fill(' ')
            // keep only the last copies
            children(ctx, tree).filter { it.second.startsWith("monolith-auto-") && it.second.endsWith(".mbk") }
                .sortedByDescending { it.second }.drop(KEEP)
                .forEach { runCatching { DocumentsContract.deleteDocument(cr, it.first) } }
            true
        }.getOrDefault(false)
        val e = p.edit().putBoolean("err", !ok)
        if (ok) e.putLong("last", now())
        e.apply()
        load(ctx)
        schedule(ctx)
        ok
    }

    private fun children(ctx: Context, tree: Uri): List<Pair<Uri, String>> {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<Pair<Uri, String>>()
        ctx.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out += DocumentsContract.buildDocumentUriUsingTree(tree, id) to (c.getString(1) ?: "")
            }
        }
        return out
    }

    // ---------------------------------------------------------------- password, locked by the keystore

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(data)
        return Base64.getEncoder().encodeToString(c.iv) + ":" + Base64.getEncoder().encodeToString(ct)
    }

    private fun decrypt(s: String): ByteArray {
        val (iv, ct) = s.split(":").let { Base64.getDecoder().decode(it[0]) to Base64.getDecoder().decode(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return c.doFinal(ct)
    }
}
