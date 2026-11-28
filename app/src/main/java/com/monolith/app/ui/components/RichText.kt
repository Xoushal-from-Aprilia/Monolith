package com.monolith.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Formatting in messages, written with markers like in other chats:
 * *bold*, _italic_, ~struck~, `code` and ||spoiler|| (hidden until tapped).
 * The markers disappear; the text keeps its look.
 */
object Rich {
    /** a styled stretch of the clean text: b bold, i italic, s struck, c code, p spoiler */
    data class Span(val start: Int, val end: Int, val kind: Char)
    data class Parsed(val text: String, val spans: List<Span>)

    private const val MARKS = "*_~`|"

    private fun boundaryBefore(raw: String, k: Int) = k == 0 || raw[k - 1].isWhitespace() || raw[k - 1] in "([{\"'«*_~|"
    private fun boundaryAfter(raw: String, k: Int) = k >= raw.length || raw[k].isWhitespace() || raw[k] in ".,;:!?)]}\"'»*_~|"

    /** where a marker opened at [from] closes on the same line, or -1 */
    private fun closeOf(raw: String, from: Int, c: Char): Int {
        var k = from + 1
        while (k < raw.length && raw[k] != '\n') {
            if (raw[k] == c && !raw[k - 1].isWhitespace() && boundaryAfter(raw, k + 1)) return k
            k++
        }
        return -1
    }

    fun parse(raw: String): Parsed {
        if (raw.none { it in MARKS }) return Parsed(raw, emptyList())
        val sb = StringBuilder()
        val spans = ArrayList<Span>()
        val open = HashMap<Char, Int>()
        val closeAt = HashMap<Char, Int>()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            // `code`: taken as it is, nothing inside is a marker
            if (c == '`') {
                val j = raw.indexOf('`', i + 1)
                if (j > i + 1 && '\n' !in raw.substring(i + 1, j)) {
                    val s = sb.length
                    sb.append(raw, i + 1, j)
                    spans.add(Span(s, sb.length, 'c'))
                    i = j + 1
                    continue
                }
            }
            // ||spoiler||
            if (c == '|' && raw.startsWith("||", i)) {
                val s = open['p']
                if (s != null) {
                    open.remove('p')
                    if (sb.length > s) spans.add(Span(s, sb.length, 'p'))
                    i += 2
                    continue
                }
                if (raw.indexOf("||", i + 2) > i + 2) {
                    open['p'] = sb.length
                    i += 2
                    continue
                }
            }
            val kind = when (c) { '*' -> 'b'; '_' -> 'i'; '~' -> 's'; else -> null }
            if (kind != null) {
                val s = open[kind]
                if (s != null && closeAt[kind] == i) {
                    open.remove(kind); closeAt.remove(kind)
                    if (sb.length > s) spans.add(Span(s, sb.length, kind))
                    i++
                    continue
                }
                if (s == null && boundaryBefore(raw, i) && i + 1 < raw.length && !raw[i + 1].isWhitespace()) {
                    val close = closeOf(raw, i, c)
                    if (close > i + 1) {
                        open[kind] = sb.length
                        closeAt[kind] = close
                        i++
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return Parsed(sb.toString(), spans)
    }

    /** the text without markers, for previews and notifications: spoilers stay hidden */
    fun strip(raw: String): String {
        val p = parse(raw)
        if (p.spans.none { it.kind == 'p' }) return p.text
        val sb = StringBuilder(p.text)
        p.spans.filter { it.kind == 'p' }.forEach { for (k in it.start until it.end) if (!sb[k].isWhitespace()) sb.setCharAt(k, '▒') }
        return sb.toString()
    }

    /**
     * A bubble's text: formatting, spoilers (tap to show), and in groups and shards the tags
     * ([names] to look for, [mine] the ones that mean me, [rooms] by name -> conversation).
     */
    fun annotate(
        raw: String,
        ink: Color,
        accent: Color,
        revealed: Boolean,
        onSpoiler: () -> Unit,
        names: List<String> = emptyList(),
        mine: List<String> = emptyList(),
        rooms: Map<String, String> = emptyMap(),
        tags: Boolean = false,
        onRoom: (String) -> Unit = {},
    ): AnnotatedString {
        val p = parse(raw)
        val tagSpans = if (tags) Tags.find(p.text, names + mine) else emptyList()
        if (p.spans.isEmpty() && tagSpans.isEmpty()) return AnnotatedString(p.text)
        val mineLow = mine.map { it.lowercase() }.toSet()
        return buildAnnotatedString {
            append(p.text)
            p.spans.forEach { s ->
                when (s.kind) {
                    'b' -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s.start, s.end)
                    'i' -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s.start, s.end)
                    's' -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s.start, s.end)
                    'c' -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = ink.copy(alpha = 0.12f)), s.start, s.end)
                    'p' -> if (!revealed) {
                        // covered by a block of the text colour: tap to see it
                        addLink(LinkAnnotation.Clickable("spoiler", TextLinkStyles(SpanStyle(color = ink, background = ink))) { onSpoiler() }, s.start, s.end)
                    } else addStyle(SpanStyle(background = ink.copy(alpha = 0.10f)), s.start, s.end)
                }
            }
            tagSpans.forEach { s ->
                val me = s.everyone || (!s.room && s.value.lowercase() in mineLow)
                val style = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, background = if (me) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.10f))
                val conv = if (s.room) rooms.entries.firstOrNull { it.key.equals(s.value, ignoreCase = true) }?.value else null
                if (conv != null) addLink(LinkAnnotation.Clickable("room:$conv", TextLinkStyles(style)) { onRoom(conv) }, s.start, s.end)
                else addStyle(style, s.start, s.end)
            }
        }
    }
}
