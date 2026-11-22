package de.vereinskasse

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import de.vereinskasse.logik.ImportDatei
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.Speicher
import de.vereinskasse.ui.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Speichert die Daten als JSON im privaten App-Speicher. */
class AndroidSpeicher(context: Context) : Speicher {
    private val datei = File(context.filesDir, "daten.json")
    private val sicherung = File(context.filesDir, "daten.vorher.json")

    override fun laden(): String? = when {
        datei.exists() -> datei.readText()
        sicherung.exists() -> sicherung.readText()
        else -> null
    }

    @Synchronized
    override fun speichern(inhalt: String) {
        val tmp = File(datei.path + ".tmp")
        tmp.writeText(inhalt)
        if (datei.exists()) datei.copyTo(sicherung, overwrite = true)
        if (!tmp.renameTo(datei)) {
            datei.writeText(inhalt)
            tmp.delete()
        }
    }
}

/** Ein Store für die ganze App – überlebt das Neuerstellen der Activity. */
object AppZustand {
    private var store: KassenStore? = null

    fun store(context: Context): KassenStore = store ?: KassenStore(
        AndroidSpeicher(context.applicationContext),
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        AndroidLogoSpeicher(context.applicationContext),
    ).also { store = it; it.logosAufraeumen() }
}

class MainActivity : ComponentActivity(), Plattform {

    private var importDatei by mutableStateOf<ImportDatei?>(null)

    private val dateiAuswahl = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) uriLesen(uri)
    }

    private var bildErgebnis: ((ByteArray?) -> Unit)? = null

    private val bildAuswahl = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val ergebnis = bildErgebnis ?: return@registerForActivityResult
        bildErgebnis = null
        if (uri == null) {
            ergebnis(null)
            return@registerForActivityResult
        }
        // Verkleinern im Hintergrund, Ergebnis auf dem Hauptthread
        Thread {
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { bildVerkleinern(it) }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (bytes == null) Toast.makeText(this, "Das Bild konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
                ergebnis(bytes)
            }
        }.start()
    }

    override fun bildAuswaehlen(ergebnis: (ByteArray?) -> Unit) {
        bildErgebnis = ergebnis
        try {
            bildAuswahl.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            bildErgebnis = null
            ergebnis(null)
            Toast.makeText(this, "Keine Bildauswahl verfügbar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = AppZustand.store(this)
        if (savedInstanceState == null) intentVerarbeiten(intent)

        setContent {
            App(
                store = store,
                plattform = this,
                importDatei = importDatei,
                importErledigt = { importDatei = null },
                zurueckHandler = { aktiv, onZurueck -> BackHandler(enabled = aktiv, onBack = onZurueck) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentVerarbeiten(intent)
    }

    override fun onStop() {
        super.onStop()
        AppZustand.store(this).sofortSpeichern()
    }

    private fun intentVerarbeiten(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uriLesen(it) }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                when {
                    uri != null -> uriLesen(uri)
                    intent.getStringExtra(Intent.EXTRA_TEXT) != null ->
                        importDatei = ImportDatei(null, intent.getStringExtra(Intent.EXTRA_TEXT)!!.encodeToByteArray())
                }
            }
        }
    }

    private fun uriLesen(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().also { require(it.size < 20_000_000) { "Datei zu groß" } }
            }
            val name = try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Exception) {
                null
            } ?: uri.lastPathSegment
            if (bytes != null) importDatei = ImportDatei(name, bytes)
        } catch (e: Exception) {
            Toast.makeText(this, "Datei konnte nicht gelesen werden: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Plattform ----------

    override fun teileText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Auswertung teilen"))
    }

    override fun teileDatei(dateiname: String, inhalt: String, mimeType: String) {
        try {
            val ordner = File(cacheDir, "export").apply { mkdirs() }
            ordner.listFiles()?.forEach { it.delete() }
            val datei = File(ordner, dateiname).apply { writeText(inhalt) }
            val uri = FileProvider.getUriForFile(this, "$packageName.dateien", datei)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, dateiname)
                clipData = ClipData.newRawUri(dateiname, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Datei teilen"))
        } catch (e: Exception) {
            Toast.makeText(this, "Teilen fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun teileBild(dateiname: String, bild: ImageBitmap) {
        try {
            val ordner = File(cacheDir, "export").apply { mkdirs() }
            ordner.listFiles()?.forEach { it.delete() }
            val datei = File(ordner, dateiname)
            datei.outputStream().use { bild.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.dateien", datei)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(dateiname, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Grafik teilen"))
        } catch (e: Exception) {
            Toast.makeText(this, "Teilen fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun dateiOeffnen() {
        try {
            dateiAuswahl.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(this, "Keine Dateiauswahl verfügbar", Toast.LENGTH_LONG).show()
        }
    }
}
