package com.monolith.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.t

/** "Friend nearby" switch in a friend's profile: works only when both of you turn it on. */
@Composable
fun NearbyCard(pk: String, name: String) {
    val ctx = LocalContext.current
    val mine by Graph.nearby.mine.collectAsState()
    val theirs by Graph.nearby.theirs.collectAsState()
    val on = pk in mine
    val they = pk in theirs
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) Graph.nearby.setMine(pk, true)
    }
    fun toggle(v: Boolean) {
        if (!v) { Graph.nearby.setMine(pk, false); return }
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ok) Graph.nearby.setMine(pk, true)
        else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.NearMe, null, Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Amico nei paraggi"), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        on && they -> t("Attivo: vi avviso quando siete a meno di 500 m")
                        on -> t("Aspetto che anche {0} lo attivi", name)
                        they -> t("{0} l'ha attivato: attivalo anche tu", name)
                        else -> t("Se lo attivate in due, un avviso quando siete a meno di 500 m")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = on, onCheckedChange = { toggle(it) })
        }
    }
}
