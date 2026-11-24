@file:OptIn(ExperimentalEncodingApi::class)

package de.vereinskasse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ComposeViewport
import de.vereinskasse.logik.ImportDatei
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.LogoSpeicher
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.Speicher
import de.vereinskasse.ui.App
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- Brücke zu vk.js ----------

@JsFun("(k) => window.vk.get(k)")
private external fun jsLaden(schluessel: String): Promise<JsString?>

@JsFun("(k, v) => window.vk.set(k, v)")
private external fun jsSpeichern(schluessel: String, wert: String): Promise<JsAny?>

@JsFun("(k) => window.vk.del(k)")
private external fun jsLoeschen(schluessel: String): Promise<JsAny?>

@JsFun("(p) => window.vk.keys(p)")
private external fun jsSchluessel(praefix: String): Promise<JsString>

@JsFun("(t) => window.vk.shareText(t)")
private external fun jsTeileText(text: String)

@JsFun("(n, i, m, b) => window.vk.shareFile(n, i, m, b)")
private external fun jsTeileDatei(name: String, inhalt: String, mime: String, base64: Boolean)

@JsFun("(a) => window.vk.pickFile(a)")
private external fun jsDateiWaehlen(accept: String): Promise<JsString?>

@JsFun("() => window.vk.fertig()")
private external fun jsFertig()

private const val SCHLUESSEL_DATEN = "daten"
private const val PRAEFIX_LOGO = "logo:"

@Serializable
private class GewaehlteDatei(val name: String, val b64: String)

private val json = Json { ignoreUnknownKeys = true }

private fun ohneFehler(p: Promise<JsAny?>) {
    p.catch { null }
}

// ---------- Speicher (IndexedDB) ----------

/** Die Daten werden beim Start einmal gelesen; Schreiben geschieht asynchron. */
private class WebSpeicher(private val geladen: String?) : Speicher {
    override fun laden() = geladen
    override fun speichern(inhalt: String) = ohneFehler(jsSpeichern(SCHLUESSEL_DATEN, inhalt))
}

private class WebLogoSpeicher(private val logos: MutableMap<String, ByteArray>) : LogoSpeicher {
    override fun laden(id: String) = logos[id]
    override fun speichern(id: String, bytes: ByteArray) {
        logos[id] = bytes
        ohneFehler(jsSpeichern(PRAEFIX_LOGO + id, Base64.encode(bytes)))
    }
    override fun loeschen(id: String) {
        logos.remove(id)
        ohneFehler(jsLoeschen(PRAEFIX_LOGO + id))
    }
    override fun alle() = logos.keys.toList()
    override fun dekodieren(bytes: ByteArray): ImageBitmap? = try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

// ---------- Plattform ----------

private class WebPlattform : Plattform {
    var importDatei by mutableStateOf<ImportDatei?>(null)
    private val scope = MainScope()

    override val speicherHinweis =
        "Die Daten liegen im Speicher des Browsers. iOS kann sie bei Speichermangel löschen – " +
            "bitte nach jeder Veranstaltung eine Sicherung erstellen."

    override val kannDateienDirektOeffnen = false

    override fun teileText(text: String) = jsTeileText(text)

    override fun teileDatei(dateiname: String, inhalt: String, mimeType: String) =
        jsTeileDatei(dateiname, inhalt, mimeType, false)

    override fun teileBild(dateiname: String, bild: ImageBitmap) {
        val png = Image.makeFromBitmap(bild.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: return
        jsTeileDatei(dateiname, Base64.encode(png), "image/png", true)
    }

    private fun waehlen(accept: String, ergebnis: (GewaehlteDatei?) -> Unit) {
        scope.launch {
            val datei = try {
                jsDateiWaehlen(accept).await<JsString?>()?.toString()?.let { json.decodeFromString(GewaehlteDatei.serializer(), it) }
            } catch (_: Throwable) {
                null
            }
            ergebnis(datei)
        }
    }

    override fun dateiOeffnen() = waehlen("") { datei ->
        if (datei != null) importDatei = ImportDatei(datei.name, Base64.decode(datei.b64))
    }

    override fun bildAuswaehlen(ergebnis: (ByteArray?) -> Unit) = waehlen("image/*") { datei ->
        ergebnis(datei?.let { bildVerkleinern(Base64.decode(it.b64)) })
    }
}

/** Verkleinert auf max. 512 px; PNG bei Transparenz, sonst JPEG. */
private fun bildVerkleinern(bytes: ByteArray): ByteArray? = try {
    val bild = Image.makeFromEncoded(bytes)
    val faktor = min(1f, 512f / max(bild.width, bild.height))
    val b = max(1, (bild.width * faktor).roundToInt())
    val h = max(1, (bild.height * faktor).roundToInt())
    val flaeche = Surface.makeRasterN32Premul(b, h)
    flaeche.canvas.drawImageRect(
        bild,
        Rect.makeWH(bild.width.toFloat(), bild.height.toFloat()),
        Rect.makeWH(b.toFloat(), h.toFloat()),
        SamplingMode.MITCHELL,
        null,
        true,
    )
    val deckend = bild.imageInfo.colorAlphaType == org.jetbrains.skia.ColorAlphaType.OPAQUE
    flaeche.makeImageSnapshot().encodeToData(if (deckend) EncodedImageFormat.JPEG else EncodedImageFormat.PNG, 88)?.bytes
} catch (_: Throwable) {
    null
}

// ---------- Start ----------

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        val daten = try {
            jsLaden(SCHLUESSEL_DATEN).await<JsString?>()?.toString()
        } catch (_: Throwable) {
            null
        }
        val logos = mutableMapOf<String, ByteArray>()
        try {
            val ids = json.decodeFromString(ListSerializer(String.serializer()), jsSchluessel(PRAEFIX_LOGO).await<JsString>().toString())
            for (id in ids) {
                jsLaden(PRAEFIX_LOGO + id).await<JsString?>()?.toString()?.let { logos[id] = Base64.decode(it) }
            }
        } catch (_: Throwable) {
        }

        val plattform = WebPlattform()
        val store = KassenStore(WebSpeicher(daten), MainScope(), WebLogoSpeicher(logos))
        store.logosAufraeumen()
        jsFertig()
        ComposeViewport("app") {
            App(store, plattform, plattform.importDatei, importErledigt = { plattform.importDatei = null })
        }
    }
}
