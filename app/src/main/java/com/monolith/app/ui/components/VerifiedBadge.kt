package com.monolith.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.net.Verified

@Composable
private fun colors(v: Verified): Pair<Color, Color> {
    val c = VerifiedStyles.color(v.color)
    return if (c == 0L) MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surface else Color(c) to Color.White
}

/** the verified symbol next to a name; nothing for accounts the developer has not verified */
@Composable
fun VerifiedBadge(pk: String, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val s by Graph.dev.state.collectAsState()
    val v = s.verified[pk] ?: return
    val (fill, mark) = colors(v)
    Canvas(modifier.padding(start = 4.dp).size(size)) { verifiedMark(v.shape, fill, mark) }
}

/** symbol and title of a verified account, under the name in the profile */
@Composable
fun VerifiedTitle(pk: String, modifier: Modifier = Modifier) {
    val s by Graph.dev.state.collectAsState()
    val v = s.verified[pk] ?: return
    val (fill, mark) = colors(v)
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) { verifiedMark(v.shape, fill, mark) }
        Spacer(Modifier.width(6.dp))
        Text(v.title.ifBlank { t("Account verificato") }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
