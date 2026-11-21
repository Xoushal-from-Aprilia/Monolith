package com.monolith.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.AutoBackup
import com.monolith.app.core.Fmt
import com.monolith.app.core.t
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings row for the weekly automatic backup to a folder of your choice. */
@Composable
fun AutoBackupRow() {
    val ctx = LocalContext.current
    val st by AutoBackup.state.collectAsState()
    var setup by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && password.length >= 6) {
            AutoBackup.enable(ctx, uri, password.toCharArray())
            Toast.makeText(ctx, t("Backup automatico attivo: il primo parte tra un minuto"), Toast.LENGTH_LONG).show()
        }
        password = ""
    }
    val subtitle = when {
        !st.on -> t("Ogni settimana, cifrato, in una cartella che scegli tu")
        st.error -> t("L'ultimo backup non è riuscito: controlla la cartella")
        st.last > 0 -> t("Ultimo: {0}", Fmt.date(st.last) + " " + Fmt.time(st.last)) + " · " + (AutoBackup.folderName(st.folder) ?: "")
        else -> t("Attivo · il primo arriva a breve")
    }
    SettingRow(
        Icons.Rounded.CloudSync, t("Backup automatico"), subtitle,
        trailing = { Switch(checked = st.on, onCheckedChange = { v -> if (v) setup = true else AutoBackup.disable(ctx) }) },
    ) { if (st.on) manage = true else setup = true }

    if (setup) {
        MonoDialog(
            onDismissRequest = { setup = false; password = "" },
            title = { Text(t("Backup automatico")) },
            text = {
                Column {
                    Text(t("Una volta a settimana Monolith salva un backup cifrato nella cartella che scegli (anche una cartella sul cloud, se il telefono la mostra). Tiene le ultime 4 copie."))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        password, { password = it }, label = { Text(t("Password")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(if (password.isNotEmpty() && password.length < 6) t("Almeno 6 caratteri") else t("Servirà per ripristinare. Resta solo su questo telefono.")) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { setup = false; pickTree.launch(null) }, enabled = password.length >= 6) { Text(t("Scegli la cartella")) }
            },
            dismissButton = { TextButton(onClick = { setup = false; password = "" }) { Text(t("Annulla")) } },
        )
    }

    if (manage) {
        MonoDialog(
            onDismissRequest = { manage = false },
            title = { Text(t("Backup automatico")) },
            text = {
                Column {
                    Text(t("Cartella: {0}", AutoBackup.folderName(st.folder) ?: "—"))
                    Spacer(Modifier.height(6.dp))
                    Text(if (st.last > 0) t("Ultimo: {0}", Fmt.date(st.last) + " " + Fmt.time(st.last)) else t("Nessun backup ancora"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    Graph.scope.launch {
                        val ok = AutoBackup.run(ctx)
                        withContext(Dispatchers.Main) {
                            busy = false
                            Toast.makeText(ctx, if (ok) t("Backup salvato") else t("Backup non riuscito"), Toast.LENGTH_LONG).show()
                        }
                    }
                }, enabled = !busy) { Text(t("Fai ora")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { manage = false; AutoBackup.disable(ctx) }) { Text(t("Disattiva")) }
                    TextButton(onClick = { manage = false }) { Text(t("Chiudi")) }
                }
            },
        )
    }
}
