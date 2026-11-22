package de.vereinskasse.logik

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Logos liegen als eigene Dateien auf dem Gerät (nicht in den Kassendaten, die nach jeder Buchung gespeichert werden). */
interface LogoSpeicher {
    fun laden(id: String): ByteArray?
    fun speichern(id: String, bytes: ByteArray)
    fun loeschen(id: String)
    fun alle(): List<String>

    /** Bild dekodieren (plattformabhängig). */
    fun dekodieren(bytes: ByteArray): ImageBitmap?
}

class LogoSpeicherImRam(private val dekodierer: (ByteArray) -> ImageBitmap? = { null }) : LogoSpeicher {
    private val logos = mutableMapOf<String, ByteArray>()
    override fun laden(id: String) = logos[id]
    override fun speichern(id: String, bytes: ByteArray) {
        logos[id] = bytes
    }
    override fun loeschen(id: String) {
        logos.remove(id)
    }
    override fun alle() = logos.keys.toList()
    override fun dekodieren(bytes: ByteArray) = dekodierer(bytes)
}

/** Größte erlaubte Logo-Datei beim Import (Schutz vor riesigen Dateien). */
const val MAX_LOGO_BYTES = 2_000_000

@OptIn(ExperimentalEncodingApi::class)
fun logoAlsBase64(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
fun logoAusBase64(text: String): ByteArray? = try {
    Base64.decode(text).takeIf { it.isNotEmpty() && it.size <= MAX_LOGO_BYTES }
} catch (_: Exception) {
    null
}

// ---------- Farben ----------

data class Hsl(val h: Float, val s: Float, val l: Float)

fun argbZuHsl(argb: Long): Hsl {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    val d = mx - mn
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = d / (1 - abs(2 * l - 1))
    val h = when (mx) {
        r -> 60 * (((g - b) / d) % 6)
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    return Hsl((h + 360) % 360, s.coerceIn(0f, 1f), l)
}

fun hslZuArgb(hsl: Hsl): Long {
    val c = (1 - abs(2 * hsl.l - 1)) * hsl.s
    val x = c * (1 - abs((hsl.h / 60) % 2 - 1))
    val m = hsl.l - c / 2
    val (r, g, b) = when {
        hsl.h < 60 -> Triple(c, x, 0f)
        hsl.h < 120 -> Triple(x, c, 0f)
        hsl.h < 180 -> Triple(0f, c, x)
        hsl.h < 240 -> Triple(0f, x, c)
        hsl.h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun k(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000 or (k(r) shl 16) or (k(g) shl 8) or k(b)
}

/** Relative Leuchtdichte nach WCAG. */
fun leuchtdichte(argb: Long): Double {
    fun kanal(v: Long): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * kanal((argb shr 16) and 0xFF) + 0.7152 * kanal((argb shr 8) and 0xFF) + 0.0722 * kanal(argb and 0xFF)
}

fun kontrast(a: Long, b: Long): Double {
    val la = leuchtdichte(a)
    val lb = leuchtdichte(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** Verändert die Helligkeit, bis der Kontrast zur Hintergrundfarbe erreicht ist. */
fun mitKontrast(farbe: Long, hintergrund: Long, ziel: Double): Long {
    var hsl = argbZuHsl(farbe)
    val dunkler = leuchtdichte(hintergrund) > 0.5
    var ergebnis = farbe
    var schritte = 0
    while (kontrast(ergebnis, hintergrund) < ziel && schritte < 100) {
        hsl = hsl.copy(l = (hsl.l + if (dunkler) -0.01f else 0.01f).coerceIn(0f, 1f))
        ergebnis = hslZuArgb(hsl)
        schritte++
    }
    return ergebnis
}

/**
 * Hauptfarbe eines Logos: die häufigste deutlich farbige Farbe (Grau, Weiß, Schwarz und
 * transparente Bereiche werden ignoriert). null, wenn das Logo keine deutliche Farbe hat.
 */
fun hauptfarbe(bild: ImageBitmap): Long? {
    val breite = bild.width
    val hoehe = bild.height
    if (breite == 0 || hoehe == 0) return null
    val pixel = IntArray(breite * hoehe)
    bild.readPixels(pixel)
    // 24 Farbton-Fächer; Gewicht = Sättigung, damit kräftige Farben gewinnen
    val gewicht = FloatArray(24)
    val summeR = FloatArray(24)
    val summeG = FloatArray(24)
    val summeB = FloatArray(24)
    var gezaehlt = 0
    val schritt = max(1, (breite * hoehe) / 40_000)
    var i = 0
    while (i < pixel.size) {
        val p = pixel[i].toLong() and 0xFFFFFFFF
        i += schritt
        if ((p shr 24) < 128) continue
        val hsl = argbZuHsl(p)
        if (hsl.s < 0.25f || hsl.l < 0.12f || hsl.l > 0.92f) continue
        val fach = (hsl.h / 15).toInt().coerceIn(0, 23)
        val w = hsl.s
        gewicht[fach] += w
        summeR[fach] += ((p shr 16) and 0xFF) * w
        summeG[fach] += ((p shr 8) and 0xFF) * w
        summeB[fach] += (p and 0xFF) * w
        gezaehlt++
    }
    if (gezaehlt < 20) return null
    val bestes = gewicht.indices.maxBy { gewicht[it] }
    val w = gewicht[bestes]
    return 0xFF000000 or ((summeR[bestes] / w).toLong() shl 16) or ((summeG[bestes] / w).toLong() shl 8) or (summeB[bestes] / w).toLong()
}
