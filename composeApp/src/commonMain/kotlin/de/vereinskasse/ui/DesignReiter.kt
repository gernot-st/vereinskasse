package de.vereinskasse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.farbeLesen
import de.vereinskasse.logik.hauptfarbe
import de.vereinskasse.model.Verein

/** Logo und Grundfarbe eines Vereins. */
@Composable
fun DesignReiter(store: KassenStore, plattform: Plattform, verein: Verein) {
    var meldung by remember { mutableStateOf<String?>(null) }
    var laedt by remember { mutableStateOf(false) }
    val logo = store.logoBild(verein.logoId)

    fun farbeSetzen(farbe: Long?) = store.vereinAendern(verein.id) { it.copy(designFarbe = farbe) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Abschnitt("Logo") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    LogoBild(logo, 96.dp)
                } else {
                    Box(
                        Modifier.size(96.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(19.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Kein Logo", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(enabled = !laedt, onClick = {
                        laedt = true
                        plattform.bildAuswaehlen { bytes ->
                            laedt = false
                            if (bytes != null) {
                                val id = store.logoSpeichern(bytes)
                                if (id == null) {
                                    meldung = "Das Bild konnte nicht gelesen werden."
                                } else {
                                    store.vereinAendern(verein.id) { it.copy(logoId = id) }
                                    store.logosAufraeumen()
                                }
                            }
                        }
                    }) { Text(if (logo == null) "Logo wählen" else "Anderes Logo") }
                    if (logo != null) {
                        TextButton(onClick = {
                            store.vereinAendern(verein.id) { it.copy(logoId = null) }
                            store.logosAufraeumen()
                        }) { Text("Logo entfernen", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Text(
                "Wird automatisch verkleinert. Erscheint an der Kasse, in der Vereinsliste und auf geteilten Grafiken " +
                    "und wird beim Teilen an andere Handys mitgeschickt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Abschnitt("Grundfarbe") {
            Text(
                "Färbt Kopfzeile, Knöpfe und Auswahl in der ganzen App, solange dieser Verein aktiv ist. " +
                    "Zu helle Farben werden für lesbare Schrift etwas abgedunkelt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FarbAuswahl(verein.designFarbe ?: -1L) { farbeSetzen(it) }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = verein.designFarbe == null,
                    onClick = { farbeSetzen(null) },
                    label = { Text("Standard") },
                )
                OutlinedButton(
                    enabled = logo != null,
                    onClick = {
                        val farbe = logo?.let { hauptfarbe(it) }
                        if (farbe == null) meldung = "Im Logo wurde keine deutliche Farbe gefunden." else farbeSetzen(farbe)
                    },
                ) { Text("Aus Logo übernehmen") }
            }
            key(verein.designFarbe) {
                var code by remember { mutableStateOf(verein.designFarbe?.let { farbCode(it) } ?: "") }
                OutlinedTextField(
                    value = code,
                    onValueChange = { text ->
                        code = text
                        val farbe = farbeLesen(text)
                        if (farbe != null && text.trim().removePrefix("#").length == 6) farbeSetzen(farbe)
                    },
                    label = { Text("Farbcode, z. B. #00696E") },
                    singleLine = true,
                    isError = code.isNotBlank() && farbeLesen(code) == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        Abschnitt("Vorschau") {
            VereinskasseTheme(grundfarbe = verein.designFarbe) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LogoBild(logo, 40.dp, Modifier.padding(end = 10.dp))
                            Text(
                                verein.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(selected = true, onClick = {}, label = { Text("Pfand: an") })
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {}) { Text("Kassieren", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    meldung?.let { HinweisDialog("Hinweis", it) { meldung = null } }
}

private fun farbCode(farbe: Long) = "#" + (farbe and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
