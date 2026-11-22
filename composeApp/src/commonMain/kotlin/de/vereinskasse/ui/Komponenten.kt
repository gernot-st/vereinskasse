package de.vereinskasse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.vereinskasse.logik.GRUPPEN_FARBEN
import de.vereinskasse.logik.geldEingabe
import de.vereinskasse.logik.parseGeld

private val HellesSchema = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0F5),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    secondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB35A00),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF301400),
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFF80D4D9),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF9CF0F5),
    secondary = Color(0xFFB0CCCD),
    secondaryContainer = Color(0xFF324B4D),
    tertiary = Color(0xFFFFB77C),
    tertiaryContainer = Color(0xFF884400),
    onTertiaryContainer = Color(0xFFFFDCC2),
)

@Composable
fun VereinskasseTheme(dunkel: Boolean = isSystemInDarkTheme(), inhalt: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dunkel) DunklesSchema else HellesSchema, content = inhalt)
}

/** Grün für positive Beträge (Rückgeld etc.). */
val Gruen = Color(0xFF2E7D32)

fun Long.alsFarbe(): Color = Color(this)

/** Gut lesbare Schriftfarbe auf einem farbigen Hintergrund. */
fun Color.textFarbe(): Color = if (luminance() > 0.45f) Color(0xFF1B1B1B) else Color.White

/** Eingabefeld für Geldbeträge. Meldet nur gültige Werte (null = leer/ungültig). */
@Composable
fun BetragFeld(
    label: String,
    startCent: Long?,
    onAenderung: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    leerErlaubt: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    var text by remember { mutableStateOf(startCent?.let { geldEingabe(it, leerBeiNull = false) } ?: "") }
    val wert = parseGeld(text)
    val fehler = text.isNotBlank() && wert == null || (!leerErlaubt && text.isBlank())
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onAenderung(parseGeld(it))
        },
        label = { Text(label) },
        suffix = { Text("€") },
        isError = fehler,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

@Composable
fun TextFeld(
    label: String,
    wert: String,
    onAenderung: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFokus: Boolean = false,
) {
    val fokus = remember { FocusRequester() }
    OutlinedTextField(
        value = wert,
        onValueChange = onAenderung,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = modifier.focusRequester(fokus),
    )
    if (autoFokus) {
        LaunchedEffect(Unit) {
            try {
                fokus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
fun BestaetigenDialog(
    titel: String,
    text: String,
    bestaetigen: String,
    onBestaetigen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onBestaetigen) { Text(bestaetigen) } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

@Composable
fun HinweisDialog(titel: String, text: String, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk,
        title = { Text(titel) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onOk) { Text("OK") } },
    )
}

/** Dialog zur Eingabe eines Namens. */
@Composable
fun NameDialog(titel: String, label: String, start: String, onSpeichern: (String) -> Unit, onAbbrechen: () -> Unit) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = { TextFeld(label, name, { name = it }, Modifier.fillMaxWidth(), autoFokus = true) },
        confirmButton = {
            TextButton(onClick = { onSpeichern(name.trim()) }, enabled = name.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarbAuswahl(gewaehlt: Long, onWahl: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (farbe in GRUPPEN_FARBEN) {
            val c = farbe.alsFarbe()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(c, CircleShape)
                    .border(
                        width = if (farbe == gewaehlt) 3.dp else 0.dp,
                        color = if (farbe == gewaehlt) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onWahl(farbe) },
                contentAlignment = Alignment.Center,
            ) {
                if (farbe == gewaehlt) Icon(Icons.Default.Check, contentDescription = "gewählt", tint = c.textFarbe())
            }
        }
    }
}

@Composable
fun FarbPunkt(farbe: Color, groesse: Int = 14) {
    Box(Modifier.size(groesse.dp).background(farbe, CircleShape))
}

/** Zeile "Bezeichnung ...... Wert" für Auswertungen. */
@Composable
fun WertZeile(
    bezeichnung: String,
    wert: String,
    modifier: Modifier = Modifier,
    hervorheben: Boolean = false,
    wertFarbe: Color = Color.Unspecified,
) {
    val stil = if (hervorheben) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(bezeichnung, style = stil, modifier = Modifier.weight(1f))
        Text(wert, style = stil, color = wertFarbe)
    }
}

@Composable
fun Abschnitt(titel: String, modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            titel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        inhalt()
    }
}
