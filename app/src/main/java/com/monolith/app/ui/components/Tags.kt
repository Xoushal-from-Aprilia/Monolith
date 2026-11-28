package com.monolith.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** a tag inside a text: someone (@Ada Rossi), everyone (@tutti) or a room of the shard (#generale) */
data class TagSpan(val start: Int, val end: Int, val room: Boolean, val everyone: Boolean, val value: String)

/**
 * Tags in groups and shards: found in the text so they can be highlighted, in the bubbles and while writing.
 * Names may have a space ("@Ada Rossi"): the known names are tried first, longest first.
 */
object Tags {
    private val EVERYONE = setOf("tutti", "all", "everyone", "tous", "alle", "все", "wszyscy", "všichni")

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'

    fun find(text: String, names: List<String> = emptyList()): List<TagSpan> {
        if ('@' !in text && '#' !in text) return emptyList()
        val sorted = names.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        val out = ArrayList<TagSpan>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val atStart = i == 0 || !isWordChar(text[i - 1])
            if ((c == '@' || c == '#') && atStart && i + 1 < text.length) {
                if (c == '@') {
                    val name = sorted.firstOrNull { n ->
                        text.regionMatches(i + 1, n, 0, n.length, ignoreCase = true) && (i + 1 + n.length >= text.length || !text[i + 1 + n.length].isLetterOrDigit())
                    }
                    if (name != null) {
                        out.add(TagSpan(i, i + 1 + name.length, room = false, everyone = false, value = name))
                        i += 1 + name.length
                        continue
                    }
                }
                var j = i + 1
                while (j < text.length && isWordChar(text[j])) j++
                // a trailing dot is the end of the sentence, not of the name
                while (j > i + 1 && text[j - 1] == '.') j--
                if (j > i + 1) {
                    val word = text.substring(i + 1, j)
                    out.add(TagSpan(i, j, room = c == '#', everyone = c == '@' && word.lowercase() in EVERYONE, value = word))
                    i = j
                    continue
                }
            }
            i++
        }
        return out
    }

    /**
     * The text of a bubble with its tags highlighted: [mine] are the names that mean me (they stand out more),
     * [rooms] the rooms of the shard by name (tapping one opens it).
     */
    fun annotate(
        text: String,
        names: List<String>,
        mine: List<String>,
        rooms: Map<String, String>,
        accent: Color,
        strong: Color,
        onRoom: (String) -> Unit,
    ): AnnotatedString {
        val spans = find(text, names + mine)
        if (spans.isEmpty()) return AnnotatedString(text)
        val mineLow = mine.map { it.lowercase() }.toSet()
        return buildAnnotatedString {
            append(text)
            spans.forEach { s ->
                val me = s.everyone || (!s.room && s.value.lowercase() in mineLow)
                val style = SpanStyle(
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    background = if (me) strong.copy(alpha = 0.22f) else accent.copy(alpha = 0.10f),
                )
                val conv = if (s.room) rooms.entries.firstOrNull { it.key.equals(s.value, ignoreCase = true) }?.value else null
                if (conv != null) {
                    addLink(LinkAnnotation.Clickable("room:$conv", TextLinkStyles(style)) { onRoom(conv) }, s.start, s.end)
                } else {
                    addStyle(style, s.start, s.end)
                }
            }
        }
    }
}

/** while writing: @names and #rooms take the accent colour as soon as they are typed */
class TagHighlight(private val color: Color, private val names: List<String>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val spans = Tags.find(text.text, names)
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(text)
        spans.forEach { b.addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.10f)), it.start, it.end) }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is TagHighlight && other.color == color && other.names == names
    override fun hashCode() = color.hashCode() * 31 + names.hashCode()
}
