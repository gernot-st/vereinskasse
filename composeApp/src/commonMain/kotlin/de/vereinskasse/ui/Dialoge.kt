package de.vereinskasse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.vereinskasse.logik.datum
import de.vereinskasse.logik.geld
import de.vereinskasse.logik.jetzt
import de.vereinskasse.logik.parseGeld
import de.vereinskasse.logik.schnellBetraege
import de.vereinskasse.model.Veranstaltung

@Composable
fun BezahlenDialog(summeCent: Long, onAbbrechen: () -> Unit, onBuchen: (gegebenCent: Long?) -> Unit) {
    Dialog(onDismissRequest = onAbbrechen, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 520.dp),
        ) {
            BezahlenInhalt(summeCent, onAbbrechen, onBuchen)
        }
    }
}

/** Inhalt des Bezahlen-Dialogs: Summe, gegebener Betrag, Rückgeld. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BezahlenInhalt(
    summeCent: Long,
    onAbbrechen: () -> Unit,
    onBuchen: (gegebenCent: Long?) -> Unit,
    startGegeben: Long? = null,
) {
    var gegeben by remember { mutableStateOf(startGegeben) }
    var eingabe by remember { mutableStateOf("") }
    val rueckgeld = gegeben?.let { it - summeCent }
    val auszahlung = summeCent < 0

    Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                auszahlung -> "Auszahlen"
                summeCent == 0L -> "Nichts zu zahlen"
                else -> "Zu zahlen"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            geld(if (auszahlung) -summeCent else summeCent),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (auszahlung) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )

        if (summeCent > 0) {
            Spacer(Modifier.height(16.dp))
            Text("Gegeben", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BetragKnopf("Passend", gegeben == summeCent) { gegeben = summeCent; eingabe = "" }
                for (betrag in schnellBetraege(summeCent)) {
                    BetragKnopf(geld(betrag), gegeben == betrag && eingabe.isEmpty()) { gegeben = betrag; eingabe = "" }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = eingabe,
                onValueChange = {
                    eingabe = it
                    gegeben = parseGeld(it)
                },
                label = { Text("Anderer Betrag") },
                suffix = { Text("€") },
                singleLine = true,
                isError = eingabe.isNotBlank() && parseGeld(eingabe) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            when {
                rueckgeld == null -> Text(
                    "Betrag wählen – oder direkt buchen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rueckgeld < 0 -> Text(
                    "Es fehlen ${geld(-rueckgeld)}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                else -> {
                    Text("Rückgeld", style = MaterialTheme.typography.titleMedium)
                    Text(geld(rueckgeld), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Gruen)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAbbrechen, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp)) {
                Text("Abbrechen")
            }
            Button(
                onClick = { onBuchen(gegeben) },
                enabled = rueckgeld == null || rueckgeld >= 0,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text("Buchen", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BetragKnopf(text: String, gewaehlt: Boolean, onKlick: () -> Unit) {
    if (gewaehlt) {
        Button(onClick = onKlick, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
            Text(text, fontSize = 17.sp)
        }
    } else {
        OutlinedButton(
            onClick = onKlick,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(),
        ) {
            Text(text, fontSize = 17.sp)
        }
    }
}

/** Anlegen oder Bearbeiten einer Veranstaltung. */
@Composable
fun VeranstaltungDialog(
    vorhanden: Veranstaltung?,
    onSpeichern: (name: String, anfangsbestandCent: Long) -> Unit,
    onLoeschen: (() -> Unit)?,
    onAbbrechen: () -> Unit,
) {
    var name by remember { mutableStateOf(vorhanden?.name ?: "Veranstaltung ${datum(jetzt())}") }
    var anfang by remember { mutableStateOf<Long?>(vorhanden?.anfangsbestandCent ?: 0) }
    var loeschenFragen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (vorhanden == null) "Neue Veranstaltung" else "Veranstaltung bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFeld("Name", name, { name = it }, Modifier.fillMaxWidth(), autoFokus = vorhanden == null)
                BetragFeld("Wechselgeld in der Kasse (Anfangsbestand)", vorhanden?.anfangsbestandCent ?: 0, { anfang = it }, Modifier.fillMaxWidth())
                if (onLoeschen != null) {
                    TextButton(onClick = { loeschenFragen = true }) {
                        Text("Veranstaltung löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeichern(name.trim(), anfang ?: 0) },
                enabled = name.isNotBlank() && anfang != null,
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )

    if (loeschenFragen && vorhanden != null && onLoeschen != null) {
        BestaetigenDialog(
            titel = "Veranstaltung löschen?",
            text = "„${vorhanden.name}“ mit ${vorhanden.bestellungen.size} Bestellungen wird endgültig gelöscht.",
            bestaetigen = "Löschen",
            onBestaetigen = { loeschenFragen = false; onLoeschen() },
            onAbbrechen = { loeschenFragen = false },
        )
    }
}
