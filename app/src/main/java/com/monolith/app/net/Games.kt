package com.monolith.app.net

import com.monolith.app.core.t
import com.monolith.app.data.Message
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Turn-based games played inside a chat message. The message extra keeps the whole game:
 * g = type, o = opponent (1:1 only), w = word (hangman), mv = [[by, value], ...].
 * Every phone replays the moves, so the board is the same for everybody.
 */
object Games {
    const val TTT = "ttt"
    const val C4 = "c4"
    const val HANG = "hang"
    const val HANG_LIVES = 6
    const val C4_COLS = 7
    const val C4_ROWS = 6

    data class Move(val by: String, val v: String)

    data class State(
        val type: String,
        val creator: String,
        val opponent: String?,
        val word: String,
        val moves: List<Move>,
    ) {
        /** the second player: fixed in a 1:1 chat, otherwise whoever answered the first move */
        val second: String? get() = opponent ?: if (type != HANG) moves.getOrNull(1)?.by else null
    }

    fun name(type: String) = when (type) {
        TTT -> t("Tris")
        C4 -> t("Forza 4")
        else -> t("Impiccato")
    }

    fun emoji(type: String) = when (type) {
        TTT -> "⭕"
        C4 -> "🔴"
        else -> "🪢"
    }

    fun parse(m: Message): State = parse(m.sender, runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()))

    fun parse(creator: String, ex: JSONObject): State {
        val a = ex.optJSONArray("mv") ?: JSONArray()
        val moves = (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { Move(it.optString(0), it.optString(1)) } }
        return State(ex.optString("g", TTT), creator, ex.optString("o").ifBlank { null }, ex.optString("w"), moves)
    }

    fun withMove(ex: JSONObject, by: String, v: String): JSONObject {
        val a = ex.optJSONArray("mv") ?: JSONArray()
        a.put(JSONArray().put(by).put(v))
        return ex.put("mv", a)
    }

    // ---------------------------------------------------------------- shared

    /** 0 = still playing, 1 = creator won, 2 = second player won, 3 = draw (or hangman lost) */
    fun result(s: State): Int = when (s.type) {
        TTT -> tttResult(tttBoard(s))
        C4 -> c4Result(c4Board(s))
        else -> hangResult(s)
    }

    /** whose turn: 1 creator, 2 second player, 0 finished */
    fun turn(s: State): Int = if (result(s) != 0) 0 else if (s.type == HANG) 2 else if (s.moves.size % 2 == 0) 1 else 2

    /** it's this person's turn (whatever the move) */
    fun canMoveAny(s: State, pk: String): Boolean = when (turn(s)) {
        1 -> pk == s.creator
        2 -> pk != s.creator && (s.second == null || s.second == pk || s.type == HANG)
        else -> false
    }

    fun canMove(s: State, pk: String, v: String): Boolean {
        val turn = turn(s)
        if (turn == 0) return false
        val allowed = when (turn) {
            1 -> pk == s.creator
            else -> pk != s.creator && (s.second == null || s.second == pk || s.type == HANG)
        }
        if (!allowed) return false
        return when (s.type) {
            TTT -> v.toIntOrNull()?.let { it in 0..8 && tttBoard(s)[it] == 0 } == true
            C4 -> v.toIntOrNull()?.let { it in 0 until C4_COLS && c4Board(s)[it] == 0 } == true
            else -> v.length == 1 && v[0].isLetter() && v.uppercase() !in guessed(s)
        }
    }

    // ---------------------------------------------------------------- tic-tac-toe

    fun tttBoard(s: State): IntArray {
        val b = IntArray(9)
        s.moves.forEachIndexed { i, mv -> mv.v.toIntOrNull()?.takeIf { it in 0..8 && b[it] == 0 }?.let { b[it] = if (i % 2 == 0) 1 else 2 } }
        return b
    }

    val TTT_LINES = listOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
    )

    fun tttWinLine(b: IntArray): IntArray? = TTT_LINES.firstOrNull { l -> b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]] }

    private fun tttResult(b: IntArray): Int {
        tttWinLine(b)?.let { return b[it[0]] }
        return if (b.all { it != 0 }) 3 else 0
    }

    // ---------------------------------------------------------------- four in a row

    /** row-major, row 0 at the top */
    fun c4Board(s: State): IntArray {
        val b = IntArray(C4_COLS * C4_ROWS)
        s.moves.forEachIndexed { i, mv ->
            val col = mv.v.toIntOrNull() ?: return@forEachIndexed
            if (col !in 0 until C4_COLS) return@forEachIndexed
            for (r in C4_ROWS - 1 downTo 0) {
                if (b[r * C4_COLS + col] == 0) { b[r * C4_COLS + col] = if (i % 2 == 0) 1 else 2; break }
            }
        }
        return b
    }

    fun c4WinCells(b: IntArray): List<Int>? {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until C4_ROWS) for (c in 0 until C4_COLS) {
            val p = b[r * C4_COLS + c]
            if (p == 0) continue
            for ((dr, dc) in dirs) {
                val cells = (0 until 4).map { k -> (r + dr * k) to (c + dc * k) }
                if (cells.all { (rr, cc) -> rr in 0 until C4_ROWS && cc in 0 until C4_COLS && b[rr * C4_COLS + cc] == p }) {
                    return cells.map { (rr, cc) -> rr * C4_COLS + cc }
                }
            }
        }
        return null
    }

    private fun c4Result(b: IntArray): Int {
        c4WinCells(b)?.let { return b[it[0]] }
        return if (b.all { it != 0 }) 3 else 0
    }

    // ---------------------------------------------------------------- hangman

    fun base(c: Char): Char = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: c

    fun guessed(s: State): List<String> = s.moves.map { it.v.uppercase() }

    fun wrong(s: State): Int {
        val letters = s.word.filter { it.isLetter() }.map { base(it) }.toSet()
        return guessed(s).count { g -> g.first() !in letters }
    }

    /** the word as the guessers see it: guessed letters, the rest as underscores */
    fun masked(s: State, reveal: Boolean): String {
        val g = guessed(s).map { it.first() }.toSet()
        return s.word.map { ch -> if (!ch.isLetter() || reveal || base(ch) in g) ch else '_' }.joinToString(" ")
    }

    private fun hangResult(s: State): Int {
        val letters = s.word.filter { it.isLetter() }.map { base(it) }.toSet()
        if (letters.isEmpty()) return 3
        val g = guessed(s).map { it.first() }.toSet()
        return when {
            g.containsAll(letters) -> 2
            wrong(s) >= HANG_LIVES -> 1
            else -> 0
        }
    }

    fun cleanWord(w: String): String = w.trim().uppercase().filter { it.isLetter() || it == ' ' || it == '\'' || it == '-' }.replace(Regex(" +"), " ").take(24)
}
