package com.monolith.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.data.BOND_BADGE_DAYS
import com.monolith.app.data.streakOf

/** a small monolith with the streak, next to a name: only for bonds of 100 days in a row or more */
@Composable
fun BondBadge(pk: String, modifier: Modifier = Modifier) {
    val days by Graph.bondDays.days.collectAsState()
    val set = days[pk]
    val streak = remember(set) { streakOf(set ?: emptySet()) }
    if (streak < BOND_BADGE_DAYS) return
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Row(
        modifier.padding(start = 6.dp).clip(CircleShape).background(ink).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp, 10.dp).clip(RoundedCornerShape(topStart = 2.5.dp, topEnd = 2.5.dp, bottomStart = 1.dp, bottomEnd = 1.dp)).background(paper))
        Spacer(Modifier.width(4.dp))
        Text(streak.toString(), color = paper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** a small star for favourite friends */
@Composable
fun FavStar(pk: String, modifier: Modifier = Modifier) {
    val favs by Graph.favorites.all.collectAsState()
    if (pk !in favs) return
    Icon(Icons.Rounded.Star, t("Preferito"), modifier.padding(start = 4.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurface)
}
