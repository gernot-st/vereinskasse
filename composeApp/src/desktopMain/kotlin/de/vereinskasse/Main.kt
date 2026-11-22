package de.vereinskasse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.vereinskasse.logik.ImportDatei
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.Speicher
import de.vereinskasse.ui.App
import java.io.File
import javax.swing.JFileChooser

/** Desktop-Version – nur zum Entwickeln und Ausprobieren der Oberfläche. */
class DateiSpeicher(private val datei: File) : Speicher {
    override fun laden(): String? = if (datei.exists()) datei.readText() else null

    @Synchronized
    override fun speichern(inhalt: String) {
        datei.parentFile?.mkdirs()
        val tmp = File(datei.path + ".tmp")
        tmp.writeText(inhalt)
        if (!tmp.renameTo(datei)) {
            datei.writeText(inhalt)
            tmp.delete()
        }
    }
}

fun main() = application {
    val scope = rememberCoroutineScope()
    val store = remember { KassenStore(
            DateiSpeicher(File(System.getProperty("user.home"), ".vereinskasse/daten.json")),
            scope,
            DateiLogoSpeicher(File(System.getProperty("user.home"), ".vereinskasse/logos")),
        ) }
    var importDatei by remember { mutableStateOf<ImportDatei?>(null) }
    val plattform = remember {
        object : Plattform {
            override fun teileText(text: String) = println("---- Teilen ----\n$text")
            override fun teileDatei(dateiname: String, inhalt: String, mimeType: String) {
                val ziel = File(System.getProperty("user.home"), dateiname)
                ziel.writeText(inhalt)
                println("Datei gespeichert: $ziel")
            }
            override fun teileBild(dateiname: String, bild: androidx.compose.ui.graphics.ImageBitmap) {
                val ziel = File(System.getProperty("user.home"), dateiname)
                javax.imageio.ImageIO.write(bild.toAwtImage(), "png", ziel)
                println("Bild gespeichert: $ziel")
            }
            override fun bildAuswaehlen(ergebnis: (ByteArray?) -> Unit) {
                val auswahl = JFileChooser()
                ergebnis(if (auswahl.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) bildVerkleinern(auswahl.selectedFile) else null)
            }
            override fun dateiOeffnen() {
                val auswahl = JFileChooser()
                if (auswahl.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    importDatei = ImportDatei(auswahl.selectedFile.name, auswahl.selectedFile.readBytes())
                }
            }
        }
    }
    Window(
        onCloseRequest = { store.sofortSpeichern(); exitApplication() },
        title = "Vereinskasse",
        state = rememberWindowState(width = 420.dp, height = 820.dp),
    ) {
        App(store, plattform, importDatei, importErledigt = { importDatei = null })
    }
}
